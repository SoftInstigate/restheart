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
 */
public final class PipelineParamScanner {

    private PipelineParamScanner() {
    }

    /** @return the distinct variable names referenced anywhere in {@code stages}, in first-seen order */
    public static Set<String> scan(BsonValue stages) {
        var names = new LinkedHashSet<String>();
        scan(stages, names);
        return names;
    }

    private static void scan(BsonValue value, Set<String> names) {
        if (value == null) {
            return;
        }

        if (value.isDocument()) {
            var doc = value.asDocument();

            if (doc.size() == 1 && doc.containsKey("$var")) {
                varName(doc.get("$var")).ifPresent(names::add);
                return;
            }

            doc.forEach((key, v) -> scan(v, names));
        } else if (value.isArray()) {
            value.asArray().forEach(v -> scan(v, names));
        }
        // scalars carry no $var references
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
