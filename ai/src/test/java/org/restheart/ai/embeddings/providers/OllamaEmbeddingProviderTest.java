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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class OllamaEmbeddingProviderTest {

    @Test
    public void parsesEmbeddingsInResponseOrder() {
        // Ollama's /api/embed already returns vectors in input order, no index field
        var body = "{\"model\":\"nomic-embed-text\",\"embeddings\":[[0.1,0.2],[0.3,0.4]]}";

        var embeddings = OllamaEmbeddingProvider.parseEmbeddings(body);

        assertEquals(2, embeddings.size());
        assertArrayEquals(new float[] {0.1f, 0.2f}, embeddings.get(0), 1e-6f);
        assertArrayEquals(new float[] {0.3f, 0.4f}, embeddings.get(1), 1e-6f);
    }

    @Test
    public void singleEmbedding_parsedAsOneElementList() {
        var body = "{\"model\":\"nomic-embed-text\",\"embeddings\":[[1.0,2.0,3.0]]}";

        var embeddings = OllamaEmbeddingProvider.parseEmbeddings(body);

        assertEquals(1, embeddings.size());
        assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, embeddings.get(0), 1e-6f);
    }

    @Test
    public void emptyEmbeddingsArray_returnsEmptyList() {
        var embeddings = OllamaEmbeddingProvider.parseEmbeddings("{\"embeddings\":[]}");
        assertEquals(0, embeddings.size());
    }
}
