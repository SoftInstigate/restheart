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
package org.restheart.ai.embeddings.providers;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;

/**
 * Response parsing shared by every provider that speaks the OpenAI
 * {@code /v1/embeddings} wire format: {@code {"data": [{"embedding": [...],
 * "index": ...}]}}. Verified independently against OpenAI, OpenRouter
 * (https://openrouter.ai/docs/api_reference/embeddings) and Voyage AI
 * (https://docs.voyageai.com/reference/embeddings-api) — all three return this
 * exact shape, differing only in endpoint URL and provider-specific optional
 * request fields (handled by each provider individually, not here).
 */
final class OpenAiWireEmbeddings {
    private OpenAiWireEmbeddings() {
    }

    /**
     * @param responseBody the raw {@code /v1/embeddings}-shaped JSON response body
     * @return one vector per input text, reordered by each entry's {@code index}
     *         since the wire format does not guarantee entries are returned in
     *         request order
     */
    static List<float[]> parse(String responseBody) {
        var respDoc = BsonDocument.parse(responseBody);
        var data = respDoc.getArray("data");

        var ordered = new float[data.size()][];
        for (var item : data) {
            var doc = item.asDocument();
            var idx = doc.getInt32("index").getValue();
            var vector = doc.getArray("embedding");
            var embedding = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                embedding[i] = (float) vector.get(i).asNumber().doubleValue();
            }
            ordered[idx] = embedding;
        }

        var result = new ArrayList<float[]>(ordered.length);
        for (var embedding : ordered) {
            result.add(embedding);
        }
        return result;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
