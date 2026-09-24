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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

public class VoyageContextualEmbeddingProviderTest {

    @Test
    public void independentTexts_eachSentAsItsOwnGroup() {
        var payload = VoyageContextualEmbeddingProvider.buildPayload(
                "voyage-context-4", List.of(List.of("a"), List.of("b"), List.of("c")), null, 0);

        var parsed = BsonDocument.parse(payload);
        var inputs = parsed.getArray("inputs");
        assertEquals(3, inputs.size());
        assertEquals(1, inputs.get(0).asArray().size());
        assertEquals("a", inputs.get(0).asArray().get(0).asString().getValue());
    }

    @Test
    public void chunksOfOneDocument_sentAsSingleGroup() {
        var payload = VoyageContextualEmbeddingProvider.buildPayload(
                "voyage-context-4", List.of(List.of("chunk1", "chunk2", "chunk3")), null, 0);

        var parsed = BsonDocument.parse(payload);
        var inputs = parsed.getArray("inputs");
        assertEquals(1, inputs.size());
        assertEquals(3, inputs.get(0).asArray().size());
    }

    @Test
    public void inputTypeOmitted_whenNotConfigured() {
        var payload = VoyageContextualEmbeddingProvider.buildPayload("voyage-context-4", List.of(List.of("a")), "", 0);
        assertFalse(payload.contains("input_type"));
    }

    @Test
    public void inputTypeIncluded_whenConfigured() {
        var payload = VoyageContextualEmbeddingProvider.buildPayload("voyage-context-4", List.of(List.of("a")), "query", 0);
        assertTrue(payload.contains("\"input_type\":\"query\""));
    }

    @Test
    public void outputDimensionOmitted_whenNotConfigured() {
        var payload = VoyageContextualEmbeddingProvider.buildPayload("voyage-context-4", List.of(List.of("a")), null, 0);
        assertFalse(payload.contains("output_dimension"));
    }

    @Test
    public void outputDimensionIncluded_whenConfigured() {
        var payload = VoyageContextualEmbeddingProvider.buildPayload("voyage-context-4", List.of(List.of("a")), null, 512);
        assertTrue(payload.contains("\"output_dimension\":512"));
    }

    @Test
    public void parseIndependent_reordersByOuterIndex() {
        var response = """
                {"data": [
                    {"index": 1, "data": [{"index": 0, "embedding": [2.0, 2.0]}]},
                    {"index": 0, "data": [{"index": 0, "embedding": [1.0, 1.0]}]}
                ], "model": "voyage-context-4"}
                """;

        var result = VoyageContextualWireEmbeddings.parseIndependent(response);
        assertEquals(2, result.size());
        assertArrayEquals(new float[]{1.0f, 1.0f}, result.get(0));
        assertArrayEquals(new float[]{2.0f, 2.0f}, result.get(1));
    }

    @Test
    public void parseChunksOfOneDocument_reordersByInnerIndex() {
        var response = """
                {"data": [
                    {"index": 0, "data": [
                        {"index": 2, "embedding": [3.0]},
                        {"index": 0, "embedding": [1.0]},
                        {"index": 1, "embedding": [2.0]}
                    ]}
                ], "model": "voyage-context-4"}
                """;

        var result = VoyageContextualWireEmbeddings.parseChunksOfOneDocument(response);
        assertEquals(3, result.size());
        assertArrayEquals(new float[]{1.0f}, result.get(0));
        assertArrayEquals(new float[]{2.0f}, result.get(1));
        assertArrayEquals(new float[]{3.0f}, result.get(2));
    }

    // -- windows and requests under the endpoint's limits (#754) ---------------------------

    private static List<String> chunksOf(int count, int chars) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> "x".repeat(chars)).toList();
    }

    @Test
    public void windows_aDocumentThatFits_isOneWindow() {
        var chunks = chunksOf(10, 300);
        assertEquals(List.of(chunks), VoyageContextualEmbeddingProvider.windows(chunks, 30_000));
    }

    @Test
    public void windows_aLargeDocument_consecutiveWindowsUnderTheLimit_inOrder() {
        // 600 chunks of 1,000 characters, about 334 tokens each: 89 fit in 30,000
        var chunks = new java.util.ArrayList<String>();
        for (int i = 0;i < 600;i++) {
            chunks.add(i + ":" + "x".repeat(1000 - String.valueOf(i).length() - 1));
        }
        var windows = VoyageContextualEmbeddingProvider.windows(chunks, 30_000);
        assertEquals(7, windows.size());
        assertEquals(chunks, windows.stream().flatMap(List::stream).toList());
        windows.forEach(w -> assertTrue(w.stream().mapToInt(VoyageContextualEmbeddingProvider::estimatedTokens).sum() <= 30_000));
    }

    @Test
    public void windows_aSingleChunkOverTheLimit_isAWindowOfItsOwn() {
        var chunks = List.of("small", "x".repeat(100_000), "small");
        assertEquals(3, VoyageContextualEmbeddingProvider.windows(chunks, 30_000).size());
    }

    @Test
    public void requests_underTheTokenChunkAndDocumentLimits() {
        var windows = VoyageContextualEmbeddingProvider.windows(chunksOf(600, 1000), 30_000);
        // 7 windows of about 29,700 tokens: 3 fit in 110,000, so 3 requests, of 3, 3 and 1
        var requests = VoyageContextualEmbeddingProvider.requests(windows, 110_000, 16_000, 1_000);
        assertEquals(3, requests.size());
        assertEquals(windows, requests.stream().flatMap(List::stream).toList());

        var singles = chunksOf(2_500, 10).stream().map(List::of).toList();
        assertEquals(3, VoyageContextualEmbeddingProvider.requests(singles, 110_000, 16_000, 1_000).size());
    }

    @Test
    public void parseDocuments_flattensDocumentsThenChunks_inTheirIndexOrder() {
        var response = """
                { "data": [
                    { "index": 1, "data": [ { "index": 1, "embedding": [4.0] }, { "index": 0, "embedding": [3.0] } ] },
                    { "index": 0, "data": [ { "index": 1, "embedding": [2.0] }, { "index": 0, "embedding": [1.0] } ] } ] }""";
        var result = VoyageContextualWireEmbeddings.parseDocuments(response);
        assertEquals(4, result.size());
        for (int i = 0;i < 4;i++) {
            assertEquals(i + 1f, result.get(i)[0]);
        }
    }
}
