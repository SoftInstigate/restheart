/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.restheart.security.EvaluationScope.Scope;
import org.restheart.security.analysis.PredicateExpression.And;
import org.restheart.security.analysis.PredicateExpression.Arg;
import org.restheart.security.analysis.PredicateExpression.Atom;
import org.restheart.security.analysis.PredicateExpression.Literal;
import org.restheart.security.analysis.PredicateExpression.Not;
import org.restheart.security.analysis.PredicateExpression.Or;

/**
 * Answers, for one permission and one candidate resource, whether some call could satisfy it.
 *
 * <p>Not whether a particular call is allowed: that is decided when the call is made, by the
 * pipeline. Here the atoms already determined are evaluated and the others are left
 * {@link Truth#UNDETERMINED}, so the result is an upper bound — never {@link Truth#FALSE} for
 * something that could have been allowed.
 *
 * <p>An instance evaluates one predicate against one candidate, because it carries the variables a
 * {@code path-template} binds along the way. Left to right, as Undertow evaluates its own.
 */
public final class ListingEvaluator {

    private final ListingContext ctx;
    private final Map<String, Optional<String>> bindings = new HashMap<>();

    /** The methods {@code io.undertow.predicate.IdempotentPredicate} considers idempotent. */
    private static final List<String> IDEMPOTENT = List.of("GET", "DELETE", "PUT", "HEAD", "OPTIONS");

    private ListingEvaluator(ListingContext ctx) {
        this.ctx = ctx;
    }

    /** Whether some call to {@code ctx}'s resource could satisfy {@code expression}. */
    public static Truth evaluate(PredicateExpression expression, ListingContext ctx) {
        return new ListingEvaluator(ctx).truthOf(expression);
    }

    /** The same question asked of a predicate that has not been parsed yet. */
    public static Truth evaluate(String predicate, ListingContext ctx) {
        return evaluate(PredicateSyntax.parse(predicate), ctx);
    }

    private Truth truthOf(PredicateExpression expression) {
        return switch (expression) {
            case Literal l -> Truth.of(l.value());
            case Not n -> truthOf(n.operand()).not();
            case And a -> truthOf(a.left()).and(truthOf(a.right()));
            case Or o -> truthOf(o.left()).or(truthOf(o.right()));
            case Atom a -> truthOf(a);
        };
    }

    // ------------------------------------------------------------ the atoms

    private Truth truthOf(Atom atom) {
        var scope = ctx.scopeOf(atom.name()).orElse(null);

        if (scope == Scope.CALL) {
            return Truth.UNDETERMINED;
        }

        var known = builtIn(atom);

        if (known != null) {
            return known;
        }

        // A predicate we have no semantics for. Invoking it is only sound when it reads the session
        // and nothing of the candidate resource, which is what LISTING means for a plugin's.
        if (scope == Scope.LISTING) {
            return ctx.evaluate(atom).map(Truth::of).orElse(Truth.UNDETERMINED);
        }

        return Truth.UNDETERMINED;
    }

    /** The predicates RESTHeart ships, evaluated here; {@code null} when the name is not one. */
    private Truth builtIn(Atom atom) {
        return switch (atom.name()) {
            case "path" -> anyOf(pathsOf(atom), p -> PathMatch.equals(p, ctx.path()));
            case "path-prefix" -> anyOf(pathsOf(atom), p -> PathMatch.prefix(p, ctx.path()));
            case "path-suffix" -> anyOf(pathsOf(atom), p -> PathMatch.suffix(p, ctx.path()));
            case "path-template" -> anyOf(pathsOf(atom), this::template);
            case "method" -> method(atom);
            case "secure" -> Truth.TRUE;
            case "idempotent" -> Truth.of(IDEMPOTENT.contains(ctx.method().toUpperCase()));
            case "equals" -> equalValues(atom);
            case "contains" -> contains(atom);
            case "exists" -> exists(atom);
            case "in" -> in(atom);
            case "gte" -> compare(atom, true);
            case "lte" -> compare(atom, false);
            case "regex" -> regex(atom);
            default -> null;
        };
    }

    /**
     * The paths an atom names. They are written as literals — the language has no way to compute
     * one — so they are taken as they were written.
     */
    private List<String> pathsOf(Atom atom) {
        var named = values(atom, "path");

        return named.isEmpty() ? values(atom, "value") : named;
    }

    private Truth method(Atom atom) {
        return anyOf(values(atom, "value"), m -> Truth.of(m.equalsIgnoreCase(ctx.method())));
    }

    private Truth equalValues(Atom atom) {
        var values = resolveAll(atom, "value");

        if (values == null) {
            return Truth.UNDETERMINED;
        }

        if (values.size() < 2) {
            return Truth.TRUE;
        }

        return Truth.of(values.stream().allMatch(v -> v.equals(values.get(0))));
    }

    private Truth contains(Atom atom) {
        var value = resolveOne(atom, "value");
        var search = values(atom, "search");

        if (value == null || search.isEmpty()) {
            return Truth.UNDETERMINED;
        }

        return Truth.of(search.stream().anyMatch(value::contains));
    }

    private Truth exists(Atom atom) {
        var value = resolveOne(atom, "value");

        return value == null ? Truth.UNDETERMINED : Truth.of(!value.isEmpty());
    }

    private Truth in(Atom atom) {
        var value = resolveOne(atom, "value");
        var array = values(atom, "array");

        if (value == null || array.isEmpty()) {
            return Truth.UNDETERMINED;
        }

        return Truth.of(array.contains(value));
    }

    /**
     * {@code gte} and {@code lte} take exactly two operands, and a side that is not a number makes
     * them false rather than true — {@code NumericComparisonPredicate}'s own rule, which this must
     * not soften: a comparison that could not be made has not been satisfied.
     */
    private Truth compare(Atom atom, boolean greater) {
        var values = resolveAll(atom, "value");

        if (values == null) {
            return Truth.UNDETERMINED;
        }

        if (values.size() != 2) {
            return Truth.FALSE;
        }

        try {
            var left = Double.parseDouble(values.get(0));
            var right = Double.parseDouble(values.get(1));

            return Truth.of(greater ? left >= right : left <= right);
        } catch (NumberFormatException e) {
            return Truth.FALSE;
        }
    }

    /**
     * {@code regex} reads the relative path when no {@code value} is given — the default
     * {@code RegularExpressionPredicate.Builder} applies — which is why a regular expression over a
     * path is determined here like any other path atom.
     */
    private Truth regex(Atom atom) {
        var patterns = values(atom, "pattern");

        if (patterns.isEmpty()) {
            return Truth.UNDETERMINED;
        }

        var target = named(atom, "value").isPresent() ? resolveOne(atom, "value") : ctx.path();

        if (target == null) {
            return Truth.UNDETERMINED;
        }

        var flags = "false".equalsIgnoreCase(single(atom, "case-sensitive")) ? Pattern.CASE_INSENSITIVE : 0;
        var fullMatch = "true".equalsIgnoreCase(single(atom, "full-match"));

        try {
            var matcher = Pattern.compile(patterns.get(0), flags).matcher(target);

            return Truth.of(fullMatch ? matcher.matches() : matcher.find());
        } catch (Exception e) {
            return Truth.UNDETERMINED;
        }
    }

    private Truth template(String pattern) {
        var match = PathMatch.template(pattern, ctx.path());

        bindings.putAll(match.bindings());

        return match.truth();
    }

    // ------------------------------------------------------------ resolution

    /**
     * The values written for a parameter: the argument carrying that name, or — when none does —
     * every argument written without one, in order. The language lets the default parameter be
     * given positionally and an array-valued one be spread, so {@code equals(a, b)} and
     * {@code equals(value={a, b})} are the same thing and must read the same.
     */
    private List<String> values(Atom atom, String name) {
        return atom.valuesOf(name);
    }

    /**
     * The value of one parameter, resolved.
     *
     * @return {@code null} when it belongs to the call, or to something we cannot read — the caller
     *         turns that into {@link Truth#UNDETERMINED}
     */
    private String resolveOne(Atom atom, String name) {
        var resolved = resolveAll(atom, name);

        return resolved == null || resolved.isEmpty() ? null : resolved.get(0);
    }

    /** Every value of one parameter, or {@code null} as soon as one of them is undetermined. */
    private List<String> resolveAll(Atom atom, String name) {
        var args = named(atom, name).map(List::of).orElseGet(atom::unnamed);
        var resolved = new ArrayList<String>();

        for (var arg : args) {
            for (var i = 0; i < arg.values().size(); i++) {
                var value = resolve(arg, i);

                if (value == null) {
                    return null;
                }

                resolved.add(value);
            }
        }

        return resolved;
    }

    private String single(Atom atom, String name) {
        return named(atom, name).map(a -> a.values().get(0)).orElse(null);
    }

    private static Optional<Arg> named(Atom atom, String name) {
        return atom.args().stream().filter(a -> name.equals(a.name())).findFirst();
    }

    /**
     * One written value, read for what it is: a quoted string is itself, {@code ${name}} is what a
     * path template bound, {@code %…} an exchange attribute, {@code @…} an ACL variable, anything
     * else a bare literal.
     */
    private String resolve(Arg arg, int i) {
        var token = arg.values().get(i);

        if (arg.isQuoted(i)) {
            return token;
        }

        if (token.startsWith("${") && token.endsWith("}")) {
            return bindings.getOrDefault(token.substring(2, token.length() - 1), Optional.empty()).orElse(null);
        }

        if (token.startsWith("%")) {
            return ofCandidate(token).or(() -> ctx.attribute(token)).orElse(null);
        }

        if (token.startsWith("@")) {
            return ctx.variable(token).orElse(null);
        }

        return token;
    }

    /**
     * The attributes that speak of the resource being considered. Only this evaluator knows which
     * one that is, so they are read here rather than asked of the context.
     */
    private Optional<String> ofCandidate(String token) {
        if ("%m".equals(token) || "%{METHOD}".equals(token)) {
            return Optional.of(ctx.method());
        }

        if (Determined.CANDIDATE_ATTRIBUTES.contains(token)) {
            return ctx.path().contains("{") ? Optional.empty() : Optional.of(ctx.path());
        }

        return Optional.empty();
    }

    private static Truth anyOf(List<String> values, java.util.function.Function<String, Truth> test) {
        var result = Truth.FALSE;

        for (var value : values) {
            result = result.or(test.apply(value));
        }

        return result;
    }
}
