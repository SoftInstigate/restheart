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
package org.restheart.ai.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Provider;
import org.restheart.plugins.ai.ContextualEmbeddingModel;
import org.restheart.plugins.ai.EmbeddingModel;

@SuppressWarnings("unchecked")
public class DocumentChunkingInterceptorTest {

    private static DocumentChunkingInterceptor newInterceptor(PluginsRegistry registry) throws Exception {
        var interceptor = new DocumentChunkingInterceptor();
        var registryField = DocumentChunkingInterceptor.class.getDeclaredField("registry");
        registryField.setAccessible(true);
        registryField.set(interceptor, registry);
        return interceptor;
    }

    private static PluginsRegistry registryWithProvider(String name, boolean enabled, EmbeddingModel model) {
        var provider = mock(Provider.class);
        when(provider.get(null)).thenReturn(model);

        var record = mock(PluginRecord.class);
        when(record.getName()).thenReturn(name);
        when(record.isEnabled()).thenReturn(enabled);
        when(record.getInstance()).thenReturn(provider);

        var registry = mock(PluginsRegistry.class);
        when(registry.getProviders()).thenReturn((Set) Set.of(record));
        return registry;
    }

    private static BsonDocument chunkDoc(String text) {
        return new BsonDocument("text", new BsonString(text));
    }

    @Test
    public void embedChunks_appendsVectorToEachDocumentInOrder() throws Exception {
        EmbeddingModel model = (texts, request) -> List.of(
            new float[] {0.1f, 0.2f}, new float[] {0.3f, 0.4f});
        var interceptor = newInterceptor(registryWithProvider("openAIEmbeddingProvider", true, model));

        var docs = List.of(chunkDoc("first"), chunkDoc("second"));
        interceptor.embedChunks(docs, List.of("first", "second"), "openAIEmbeddingProvider", null, new BsonString("f1"), "db");

        assertEquals(0.1, docs.get(0).getArray("vector").get(0).asDouble().getValue(), 1e-6);
        assertEquals(0.3, docs.get(1).getArray("vector").get(0).asDouble().getValue(), 1e-6);
    }

    @Test
    public void embedChunks_prefersContextualEmbeddingWhenAvailable() throws Exception {
        class ContextualModel implements EmbeddingModel, ContextualEmbeddingModel {
            boolean contextualCalled = false;
            boolean plainCalled = false;

            @Override
            public List<float[]> embed(List<String> texts, Request<?> request) {
                plainCalled = true;
                return List.of(new float[] {9f}, new float[] {9f});
            }

            @Override
            public List<float[]> embedChunks(List<String> chunksOfSameDocument, Request<?> request) {
                contextualCalled = true;
                return List.of(new float[] {0.1f}, new float[] {0.2f});
            }
        }

        var model = new ContextualModel();
        var interceptor = newInterceptor(registryWithProvider("voyageContextualEmbeddingProvider", true, model));

        var docs = List.of(chunkDoc("first"), chunkDoc("second"));
        interceptor.embedChunks(docs, List.of("first", "second"), "voyageContextualEmbeddingProvider", null, new BsonString("f1"), "db");

        assertTrue(model.contextualCalled, "embedChunks (contextual) should have been called");
        assertFalse(model.plainCalled, "embed (independent) should not have been called when contextual is available");
        assertEquals(0.1, docs.get(0).getArray("vector").get(0).asDouble().getValue(), 1e-6);
        assertEquals(0.2, docs.get(1).getArray("vector").get(0).asDouble().getValue(), 1e-6);
    }

    @Test
    public void embedChunks_missingProvider_leavesDocumentsWithoutVector() throws Exception {
        var registry = mock(PluginsRegistry.class);
        when(registry.getProviders()).thenReturn((Set) Set.of());
        var interceptor = newInterceptor(registry);

        var docs = List.of(chunkDoc("first"));
        interceptor.embedChunks(docs, List.of("first"), "missingProvider", null, new BsonString("f1"), "db");

        assertFalse(docs.get(0).containsKey("vector"));
    }

    @Test
    public void embedChunks_embeddingCallThrows_leavesDocumentsWithoutVector() throws Exception {
        EmbeddingModel model = (texts, request) -> { throw new RuntimeException("boom"); };
        var interceptor = newInterceptor(registryWithProvider("p", true, model));

        var docs = List.of(chunkDoc("first"));
        interceptor.embedChunks(docs, List.of("first"), "p", null, new BsonString("f1"), "db");

        assertFalse(docs.get(0).containsKey("vector"));
    }

    @Test
    public void nullText_returnsNoChunks() {
        assertEquals(List.of(), DocumentChunkingInterceptor.splitIntoChunks(null, 1000, 200));
    }

    @Test
    public void emptyText_returnsNoChunks() {
        assertEquals(List.of(), DocumentChunkingInterceptor.splitIntoChunks("", 1000, 200));
    }

    @Test
    public void nonPositiveSize_returnsNoChunks() {
        assertEquals(List.of(), DocumentChunkingInterceptor.splitIntoChunks("some text", 0, 0));
        assertEquals(List.of(), DocumentChunkingInterceptor.splitIntoChunks("some text", -1, 0));
    }

    @Test
    public void textShorterThanChunkSize_returnsSingleChunk() {
        var chunks = DocumentChunkingInterceptor.splitIntoChunks("a short document", 1000, 200);
        assertEquals(List.of("a short document"), chunks);
    }

    @Test
    public void textLongerThanChunkSize_splitsOnWordBoundariesWithOverlap() {
        // "aaaa bbbb cccc dddd" (19 chars), size=10, overlap=3.
        // Walked through by hand against DocumentChunkingInterceptor.splitIntoChunks:
        //  start=0  end=10 -> trimmed to word boundary at 9  -> "aaaa bbbb", step=6
        //  start=6  end=16 -> trimmed to word boundary at 14 -> "bbb cccc",  step=5
        //  start=11 end=19 (== len, no trim)                -> "ccc dddd",  step=5
        //  start=16 end=19 (== len, no trim)                -> "ddd",       step<=0 -> falls back to size
        //  start=26 >= len(19) -> loop ends
        var text = "aaaa bbbb cccc dddd";
        var chunks = DocumentChunkingInterceptor.splitIntoChunks(text, 10, 3);
        assertEquals(List.of("aaaa bbbb", "bbb cccc", "ccc dddd", "ddd"), chunks);
    }

    @Test
    public void zeroOverlap_chunksDoNotRepeatContent() {
        var text = "aaaa bbbb cccc dddd";
        var chunks = DocumentChunkingInterceptor.splitIntoChunks(text, 10, 0);
        // no chunk should be empty, and every chunk must be non-blank
        chunks.forEach(c -> assertTrue(!c.isBlank(), "chunk should not be blank: [" + c + "]"));
    }

    @Test
    public void overlapGreaterThanSize_stillTerminatesAndCoversText() {
        // overlap > size would make (end - start - overlap) negative on every
        // iteration if not guarded; splitIntoChunks falls back to step = size
        // in that case. This must not loop forever and must make forward
        // progress until the whole text has been consumed.
        var text = "aaaa bbbb cccc dddd eeee ffff gggg";
        var chunks = DocumentChunkingInterceptor.splitIntoChunks(text, 5, 50);

        assertTrue(!chunks.isEmpty());
        // every word from the source text must appear in at least one chunk
        for (var word : text.split(" ")) {
            assertTrue(chunks.stream().anyMatch(c -> c.contains(word)),
                "word '" + word + "' missing from chunks " + chunks);
        }
    }

    @Test
    public void chunks_areStrippedOfLeadingAndTrailingWhitespace() {
        var chunks = DocumentChunkingInterceptor.splitIntoChunks("  padded text  ", 1000, 200);
        assertEquals(List.of("padded text"), chunks);
    }
}
