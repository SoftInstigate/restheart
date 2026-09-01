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

import org.bson.BsonArray;
import org.bson.BsonDocument;

/**
 * Response parsing for Voyage AI's {@code /v1/contextualizedembeddings} wire format:
 * {@code {"data": [{"data": [{"embedding": [...], "index": ...}, ...], "index": ...},
 * ...]}} — one outer entry per input group (each a {@code List<String>} in the
 * request's {@code inputs}), each with its own inner per-chunk entries. Neither
 * nesting level guarantees request order, so both are reordered by their own
 * {@code index}.
 */
final class VoyageContextualWireEmbeddings {
    private VoyageContextualWireEmbeddings() {
    }

    /**
     * For a request where every input text was sent as its own single-text group
     * (independent embeddings — {@link org.restheart.plugins.ai.EmbeddingModel#embed}).
     *
     * @return one vector per input text, reordered by each outer entry's {@code index}
     */
    static List<float[]> parseIndependent(String responseBody) {
        var groups = BsonDocument.parse(responseBody).getArray("data");

        var ordered = new float[groups.size()][];
        for (var g : groups) {
            var groupDoc = g.asDocument();
            var groupIdx = groupDoc.getInt32("index").getValue();
            var inner = groupDoc.getArray("data");
            ordered[groupIdx] = toFloatArray(inner.get(0).asDocument().getArray("embedding"));
        }
        return toList(ordered);
    }

    /**
     * For a request where every chunk of one document was sent as a single group
     * ({@link org.restheart.plugins.ai.ContextualEmbeddingModel#embedChunks}).
     *
     * @return one vector per chunk, reordered by each inner entry's {@code index}
     */
    static List<float[]> parseChunksOfOneDocument(String responseBody) {
        var groups = BsonDocument.parse(responseBody).getArray("data");
        var inner = groups.get(0).asDocument().getArray("data");

        var ordered = new float[inner.size()][];
        for (var item : inner) {
            var doc = item.asDocument();
            var idx = doc.getInt32("index").getValue();
            ordered[idx] = toFloatArray(doc.getArray("embedding"));
        }
        return toList(ordered);
    }

    private static float[] toFloatArray(BsonArray vector) {
        var embedding = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            embedding[i] = (float) vector.get(i).asNumber().doubleValue();
        }
        return embedding;
    }

    private static List<float[]> toList(float[][] ordered) {
        var result = new ArrayList<float[]>(ordered.length);
        for (var embedding : ordered) {
            result.add(embedding);
        }
        return result;
    }
}
