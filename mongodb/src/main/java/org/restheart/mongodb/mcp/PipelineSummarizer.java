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

import java.util.ArrayList;

import org.bson.BsonValue;

/**
 * Best-effort, human-readable summary of an aggregation/change-stream pipeline — an agent
 * hint, not a precise description. Lists each stage's top-level operator(s) in order, e.g.
 * {@code $match → $group → $sort}. An operator can override it via {@code mcp.pipeline_summary}
 * (see restheart#616 open questions) when the heuristic isn't good enough.
 */
public final class PipelineSummarizer {

    private PipelineSummarizer() {
    }

    public static String summarize(BsonValue stages) {
        if (stages == null || !stages.isArray()) {
            return "";
        }

        var parts = new ArrayList<String>();
        for (var stage : stages.asArray()) {
            if (stage.isDocument() && !stage.asDocument().isEmpty()) {
                parts.add(String.join("+", stage.asDocument().keySet()));
            } else {
                parts.add("?");
            }
        }

        return String.join(" → ", parts);
    }
}
