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
package org.restheart.ai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.ai.util.CollectionEmbeddingConfig.EmbeddingRule;
import org.restheart.exchange.Request;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Provider;
import org.restheart.plugins.ai.ContextualEmbeddingModel;
import org.restheart.plugins.ai.EmbeddingModel;

@SuppressWarnings("unchecked")
public class RuleEmbedderTest {

    private static PluginsRegistry registryWith(String name, EmbeddingModel model) {
        var provider = mock(Provider.class);
        when(provider.get(null)).thenReturn(model);
        var record = mock(PluginRecord.class);
        when(record.getName()).thenReturn(name);
        when(record.isEnabled()).thenReturn(true);
        when(record.getInstance()).thenReturn(provider);
        var registry = mock(PluginsRegistry.class);
        when(registry.getProviders()).thenReturn((Set) Set.of(record));
        return registry;
    }

    private static BsonDocument doc(String text, String fileId) {
        var d = new BsonDocument("text", new BsonString(text));
        if (fileId != null) {
            d.put("fileId", new BsonString(fileId));
        }
        return d;
    }

    /** A contextual model that records its calls and answers [group size, position] per chunk. */
    static class RecordingContextual implements EmbeddingModel, ContextualEmbeddingModel {
        final List<List<String>> groups = new ArrayList<>();
        final List<List<String>> plain = new ArrayList<>();

        @Override
        public List<float[]> embed(List<String> texts, Request<?> request) {
            plain.add(texts);
            return texts.stream().map(t -> new float[]{1f, 0f}).toList();
        }

        @Override
        public List<float[]> embedChunks(List<String> chunks, Request<?> request) {
            groups.add(chunks);
            var out = new ArrayList<float[]>();
            for (int i = 0;i < chunks.size();i++) {
                out.add(new float[]{chunks.size(), i});
            }
            return out;
        }
    }

    @Test
    public void groupBy_withAContextualModel_embedsEachGroupTogether() {
        var model = new RecordingContextual();
        var embedder = new RuleEmbedder(registryWith("ctx", model), "ctx");
        var docs = List.of(doc("a1", "A"), doc("b1", "B"), doc("a2", "A"), doc("a3", "A"));

        var warnings = embedder.embed(null, List.of(new EmbeddingRule("text", "vector", "ctx", null, null, "fileId")), docs);

        assertTrue(warnings.isEmpty());
        assertEquals(List.of(List.of("a1", "a2", "a3"), List.of("b1")), model.groups);
        assertTrue(model.plain.isEmpty());
        assertEquals(3.0, docs.get(0).getArray("vector").get(0).asDouble().getValue());
        assertEquals(2.0, docs.get(3).getArray("vector").get(1).asDouble().getValue());
        assertEquals(1.0, docs.get(1).getArray("vector").get(0).asDouble().getValue());
    }

    @Test
    public void withoutGroupBy_eachDocumentOnItsOwn_inOneCall() {
        var model = new RecordingContextual();
        var embedder = new RuleEmbedder(registryWith("ctx", model), "ctx");
        var docs = List.of(doc("a1", "A"), doc("a2", "A"));

        embedder.embed(null, List.of(new EmbeddingRule("text", "vector", "ctx", null, null)), docs);

        assertTrue(model.groups.isEmpty());
        assertEquals(List.of(List.of("a1", "a2")), model.plain);
    }

    @Test
    public void groupBy_withAModelThatIsNotContextual_isIgnored() {
        var calls = new ArrayList<List<String>>();
        EmbeddingModel model = (texts, request) -> {
            calls.add(texts);
            return texts.stream().map(t -> new float[]{0.5f}).toList();
        };
        var embedder = new RuleEmbedder(registryWith("p", model), "p");
        var docs = List.of(doc("a1", "A"), doc("b1", "B"));

        embedder.embed(null, List.of(new EmbeddingRule("text", "vector", "p", null, null, "fileId")), docs);

        assertEquals(List.of(List.of("a1", "b1")), calls);
        assertTrue(docs.get(1).containsKey("vector"));
    }

    @Test
    public void aDocumentWithoutTheTextField_isLeftAsItIs() {
        EmbeddingModel model = (texts, request) -> texts.stream().map(t -> new float[]{0.5f}).toList();
        var embedder = new RuleEmbedder(registryWith("p", model), "p");
        var without = new BsonDocument("title", new BsonString("no text"));
        var docs = List.of(without, doc("t", null));

        embedder.embed(null, List.of(new EmbeddingRule("text", "vector", "p", null, null)), docs);

        assertFalse(without.containsKey("vector"));
        assertTrue(docs.get(1).containsKey("vector"));
    }

    @Test
    public void aFailure_isAWarning_andTheDocumentsStayWithoutVector() {
        EmbeddingModel model = (texts, request) -> {
            throw new IllegalStateException("vendor down");
        };
        var embedder = new RuleEmbedder(registryWith("p", model), "p");
        var docs = List.of(doc("t", null));

        var warnings = embedder.embed(null, List.of(new EmbeddingRule("text", "vector", "p", null, null)), docs);

        assertEquals(List.of("auto-embedding of 'vector' failed: vendor down"), warnings);
        assertFalse(docs.get(0).containsKey("vector"));
    }

    @Test
    public void aPartialFailure_keepsTheVectorsThatCame_andSaysHowManyAreMissing() {
        var model = new RecordingContextual() {
            @Override
            public List<float[]> embedChunks(List<String> chunks, Request<?> request) {
                var out = new ArrayList<float[]>();
                for (int i = 0;i < chunks.size();i++) {
                    out.add(i == 0 ? null : new float[]{1f});
                }
                return out;
            }
        };
        var embedder = new RuleEmbedder(registryWith("ctx", model), "ctx");
        var docs = List.of(doc("a1", "A"), doc("a2", "A"), doc("a3", "A"));

        var warnings = embedder.embed(null, List.of(new EmbeddingRule("text", "vector", "ctx", null, null, "fileId")), docs);

        assertEquals(List.of("auto-embedding of 'vector' incomplete: 1 of 3 documents without a vector, a request to 'ctx' failed"), warnings);
        assertFalse(docs.get(0).containsKey("vector"));
        assertTrue(docs.get(2).containsKey("vector"));
    }

    @Test
    public void aMissingProvider_isAWarning_skipped() {
        var registry = mock(PluginsRegistry.class);
        when(registry.getProviders()).thenReturn((Set) Set.of());
        var embedder = new RuleEmbedder(registry, "nope");

        var warnings = embedder.embed(null, List.of(new EmbeddingRule("text", "vector", null, null, null)), List.of(doc("t", null)));

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("auto-embedding of 'vector' skipped: embedding provider 'nope'"), warnings.get(0));
    }

    @Test
    public void apply_writesDoubles_andANullVectorLeavesTheDocument() {
        var d = new BsonDocument();
        RuleEmbedder.apply(d, new float[]{0.1f, 0.2f}, "v");
        assertEquals(2, d.getArray("v").size());
        var e = new BsonDocument();
        RuleEmbedder.apply(e, null, "v");
        assertFalse(e.containsKey("v"));
    }
}
