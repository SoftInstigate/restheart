/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.rerank.providers;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.restheart.plugins.ai.RankedResult;

/**
 * Response parsing shared by Cohere and Voyage's rerank APIs — both return each
 * result as {@code {"index": N, "relevance_score": F}}, differing only in the name
 * of the top-level array field (Cohere: {@code results}; Voyage: {@code data}) and
 * the request field for limiting result count (Cohere: {@code top_n}; Voyage:
 * {@code top_k} — handled by each provider individually, not here). Verified against
 * https://docs.cohere.com/reference/rerank and
 * https://docs.voyageai.com/reference/reranker-api on 2026-09-01.
 */
final class RerankWireParsing {
    private RerankWireParsing() {
    }

    /**
     * @param responseBody the raw response body
     * @param arrayFieldName the top-level field holding the results array
     *        ({@code "results"} for Cohere, {@code "data"} for Voyage)
     */
    static List<RankedResult> parse(String responseBody, String arrayFieldName) {
        var respDoc = BsonDocument.parse(responseBody);
        var array = respDoc.getArray(arrayFieldName);

        var results = new ArrayList<RankedResult>(array.size());
        for (var item : array) {
            var doc = item.asDocument();
            var index = doc.getInt32("index").getValue();
            var score = doc.getDouble("relevance_score").getValue();
            results.add(new RankedResult(index, score));
        }
        return results;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
