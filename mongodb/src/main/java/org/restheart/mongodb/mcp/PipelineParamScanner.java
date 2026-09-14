/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.mcp;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.bson.BsonValue;

/**
 * Finds every {@code $var} reference in an aggregation/change-stream pipeline — the same
 * operator format {@code org.restheart.mongodb.utils.VarsInterpolator} resolves at request
 * time ({@code {"$var": "name"}} or {@code {"$var": ["name", defaultValue]}}) — so that an
 * {@code AggregationMcpResourceBuilder}/{@code ChangeStreamMcpResourceBuilder} can declare
 * accurate {@code params} without the operator having to name every variable twice (once in
 * the pipeline, once in the {@code mcp.params} block).
 *
 * <p>Also derives, purely from pipeline shape, which of those variables {@code
 * StagesInterpolator} would actually reject as unbound ({@code QueryVariableNotBoundException})
 * if the caller supplied nothing — i.e. which are genuinely required. A variable is required
 * only if it appears, at least once, as a bare {@code {"$var": "name"}} reference (no default —
 * the array form {@code {"$var": ["name", defaultValue]}} always has a fallback) outside any
 * {@code $ifvar}/{@code $ifarg} conditional stage (whose own condition variable, and anything
 * referenced only inside the stage it guards, is never bound-or-throw — the stage is simply
 * skipped when the condition variable is absent).
 */
public final class PipelineParamScanner {

    private PipelineParamScanner() {
    }

    /**
     * The distinct variable names referenced anywhere in a pipeline, which of those are required,
     * and which of RESTHeart's own it uses.
     *
     * <p>{@code names} holds only what a caller supplies. {@code predefined} holds the rest — the
     * {@code @}-prefixed ones RESTHeart binds itself — kept rather than dropped because a pipeline
     * paginating with {@code @skip}/{@code @limit} is a pipeline that answers to {@code ?page} and
     * {@code ?pagesize}, and an agent has to be told so.
     */
    public record ScanResult(Set<String> names, Set<String> required, Set<String> predefined) {
        public boolean isRequired(String name) {
            return required.contains(name);
        }
    }

    /** @return the variables referenced anywhere in {@code stages}, in first-seen order, with their required/optional status */
    public static ScanResult scan(BsonValue stages) {
        var names = new LinkedHashSet<String>();
        var predefined = new LinkedHashSet<String>();
        var required = new LinkedHashSet<String>();
        scan(stages, names, required, predefined, false);
        return new ScanResult(names, required, predefined);
    }

    /**
     * Whether RESTHeart binds this variable itself, so no caller ever supplies it.
     *
     * <p>{@code StagesInterpolator.injectAvars} puts {@code @page}, {@code @pagesize},
     * {@code @limit}, {@code @skip}, {@code @user} and {@code @mongoPermissions} into the avars of
     * every request before a pipeline is interpolated. They are never unbound and never the
     * caller's to set.
     *
     * <p>Counting them made a paginated aggregation — {@code {"$skip": {"$var": "@skip"}}}, which
     * is what the console's own "add pagination" writes — advertise {@code @skip} and
     * {@code @limit} to agents as required string parameters, and warn that they were undeclared.
     * An agent would then pass them, and an operator could only silence the warning by declaring
     * something no agent should send.
     *
     * <p>The {@code @} prefix is the whole rule: the tenant-id syntax for a variable name has no
     * use for it, so nothing a caller can name collides with one of these.
     */
    private static boolean isPredefined(String name) {
        return name.startsWith("@");
    }

    /** The predefined variables that follow {@code ?page} and {@code ?pagesize}. */
    private static final Set<String> PAGINATION = Set.of("@page", "@pagesize", "@limit", "@skip");

    /** Whether this pipeline pages itself, and so answers to {@code ?page} and {@code ?pagesize}. */
    public static boolean paginates(ScanResult scan) {
        return scan.predefined().stream().anyMatch(PAGINATION::contains);
    }

    private static void scan(BsonValue value, Set<String> names, Set<String> required, Set<String> predefined, boolean insideConditionalStage) {
        if (value == null) {
            return;
        }

        if (value.isDocument()) {
            var doc = value.asDocument();

            if (doc.size() == 1 && doc.containsKey("$var")) {
                var varValue = doc.get("$var");
                varName(varValue).ifPresent(name -> {
                    if (isPredefined(name)) {
                        predefined.add(name);
                        return;
                    }

                    names.add(name);
                    var hasDefault = varValue.isArray();
                    if (!hasDefault && !insideConditionalStage) {
                        required.add(name);
                    }
                });
                return;
            }

            if (doc.size() == 1 && (doc.containsKey("$ifvar") || doc.containsKey("$ifarg")) && doc.values().iterator().next().isArray()) {
                var elements = doc.values().iterator().next().asArray();
                // element 0: the condition variable name(s) — inherently optional, that's the
                // whole point of $ifvar/$ifarg (the stage runs only when it's present)
                conditionNames(elements.isEmpty() ? null : elements.get(0))
                        .forEach(name -> (isPredefined(name) ? predefined : names).add(name));
                // elements 1+ (then/else stage bodies): descend as conditional — nothing in here
                // can throw QueryVariableNotBoundException, since the whole stage is skipped
                // when the condition variable is missing
                for (var i = 1;i < elements.size();i++) {
                    scan(elements.get(i), names, required, predefined, true);
                }
                return;
            }

            doc.forEach((key, v) -> scan(v, names, required, predefined, insideConditionalStage));
        } else if (value.isArray()) {
            value.asArray().forEach(v -> scan(v, names, required, predefined, insideConditionalStage));
        }
        // scalars carry no $var references
    }

    private static Set<String> conditionNames(BsonValue condition) {
        if (condition == null) {
            return Set.of();
        }
        if (condition.isString()) {
            return Set.of(condition.asString().getValue());
        }
        if (condition.isArray()) {
            var names = new LinkedHashSet<String>();
            condition.asArray().stream().filter(BsonValue::isString).forEach(v -> names.add(v.asString().getValue()));
            return names;
        }
        return Set.of();
    }

    private static Optional<String> varName(BsonValue varValue) {
        if (varValue.isString()) {
            return Optional.of(varValue.asString().getValue());
        }
        if (varValue.isArray() && !varValue.asArray().isEmpty() && varValue.asArray().get(0).isString()) {
            return Optional.of(varValue.asArray().get(0).asString().getValue());
        }
        return Optional.empty();
    }
}
