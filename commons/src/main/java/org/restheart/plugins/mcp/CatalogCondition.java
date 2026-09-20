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
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins.mcp;

import java.util.List;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.security.EvaluationScope.Scope;
import org.restheart.security.analysis.Determined;
import org.restheart.security.analysis.PredicateExpression;
import org.restheart.security.analysis.PredicateExpression.And;
import org.restheart.security.analysis.PredicateExpression.Atom;
import org.restheart.security.analysis.PredicateExpression.Not;
import org.restheart.security.analysis.PredicateExpression.Or;
import org.restheart.security.analysis.PredicateScopes;
import org.restheart.security.analysis.PredicateSyntax;

/**
 * The two keys of an {@code mcp} block that say who a resource is announced to.
 *
 * <p>They decide disclosure, not access: a caller the condition showed still gets the ACL's 403,
 * and one it hid could have read the resource perfectly well. That is the whole reason they are
 * allowed to exist — declaring what to publish is the publisher's business, declaring who may read
 * is the ACL's, and a second copy of the second would drift from the first in silence.
 *
 * <p>They are for what the analysis of the permissions cannot reach: a resource the ACL does allow
 * and whose name must not appear anyway, and a resource whose access an authorizer written in Java
 * decides, where there is no rule to read.
 *
 * <pre>
 * "mcp": { "description": "...", "hide_from_roles": ["employee"] }
 * "mcp": { "description": "...", "show_if": "equals(@user.plan, 'gold')" }
 * </pre>
 */
public final class CatalogCondition {

    public static final String SHOW_IF = "show_if";
    public static final String HIDE_FROM_ROLES = "hide_from_roles";

    private CatalogCondition() {
    }

    /** The {@code show_if} predicate of an {@code mcp} block, or {@code null}. */
    public static String showIf(BsonDocument mcp) {
        return mcp != null && mcp.get(SHOW_IF) instanceof BsonString s ? s.getValue() : null;
    }

    /** The roles of {@code hide_from_roles}, empty when the key is absent. */
    public static Set<String> hideFromRoles(BsonDocument mcp) {
        if (mcp == null || !(mcp.get(HIDE_FROM_ROLES) instanceof BsonArray roles)) {
            return Set.of();
        }

        return roles.stream()
                .filter(r -> r.isString())
                .map(r -> r.asString().getValue())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Why {@code showIf} cannot be used, or empty when it can.
     *
     * <p>A catalog condition may only read what is determined when a listing is composed. One that
     * reads a query parameter or a body has no meaning there — the datum does not exist yet — and
     * that is an authoring mistake, not a case to resolve by showing: the author asked a question
     * nobody can answer, and the honest answer is to say so.
     *
     * @return the reason, ready to be logged or returned to whoever wrote it
     */
    public static List<String> problemsWith(String showIf) {
        if (showIf == null) {
            return List.of();
        }

        try {
            return undetermined(PredicateSyntax.parse(showIf)).stream()
                    .map(atom -> "'" + atom + "' cannot be evaluated when a catalogue is composed: "
                            + "it reads the call, which has not been made, or nothing declares when it can be read")
                    .toList();
        } catch (PredicateSyntax.SyntaxException e) {
            return List.of("not a valid predicate: " + e.getMessage());
        }
    }

    /** The atoms of {@code expression} that a listing cannot determine. */
    private static List<String> undetermined(PredicateExpression expression) {
        return switch (expression) {
            case And a -> concat(undetermined(a.left()), undetermined(a.right()));
            case Or o -> concat(undetermined(o.left()), undetermined(o.right()));
            case Not n -> undetermined(n.operand());
            case Atom a -> undetermined(a);
            default -> List.of();
        };
    }

    /**
     * One atom. A predicate that reads the call is undetermined whatever its arguments; one that
     * takes the class of its arguments is undetermined when any of them does; one nobody declares
     * is undetermined because we cannot know.
     */
    private static List<String> undetermined(Atom atom) {
        var scope = PredicateScopes.of(atom.name()).orElse(null);

        if (scope == Scope.LISTING) {
            return List.of();
        }

        if (scope == Scope.ARGUMENTS) {
            var determined = atom.args().stream().allMatch(arg -> {
                for (var i = 0; i < arg.values().size(); i++) {
                    if (!Determined.argument(arg.values().get(i), arg.isQuoted(i))) {
                        return false;
                    }
                }
                return true;
            });

            return determined ? List.of() : List.of(atom.text());
        }

        return List.of(atom.text());
    }

    private static List<String> concat(List<String> a, List<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).distinct().toList();
    }
}
