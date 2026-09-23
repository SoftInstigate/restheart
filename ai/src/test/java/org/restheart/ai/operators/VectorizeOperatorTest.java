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
package org.restheart.ai.operators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.mongodb.utils.CustomOperator.Placement;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Provider;
import org.restheart.plugins.ai.EmbeddingModel;

@SuppressWarnings("unchecked")
public class VectorizeOperatorTest {

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

    /** A registry with one provider per (name, vector length), each answering a vector of its length. */
    private static PluginsRegistry registryWithProviders(Map<String, Integer> lengths) {
        var records = new java.util.HashSet<PluginRecord>();
        lengths.forEach((name, length) -> {
            EmbeddingModel model = (texts, request) -> List.of(new float[length]);
            var provider = mock(Provider.class);
            when(provider.get(null)).thenReturn(model);
            var record = mock(PluginRecord.class);
            when(record.getName()).thenReturn(name);
            when(record.isEnabled()).thenReturn(true);
            when(record.getInstance()).thenReturn(provider);
            records.add(record);
        });
        var registry = mock(PluginsRegistry.class);
        when(registry.getProviders()).thenReturn((Set) records);
        return registry;
    }

    /** A MongoRequest whose attached params are a real map, so attach/restore can be observed. */
    private static MongoRequest mongoRequest(BsonDocument collectionProps, Map<String, Object> state) {
        var req = mock(MongoRequest.class);
        when(req.getCollectionProps()).thenReturn(collectionProps);
        when(req.attachedParam(anyString())).thenAnswer(inv -> state.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> state.put(inv.getArgument(0, String.class), inv.getArgument(1))).when(req).attachParam(anyString(), any());
        return req;
    }

    private static final BsonDocument TWO_RULES = BsonDocument.parse("""
        { "vectorSearch": [
            { "textField": "summary", "embeddingField": "summaryVector", "provider": "p3" },
            { "textField": "body", "embeddingField": "bodyVector", "provider": "p5" } ] }""");

    private static Placement queryVectorOf(String stage, String path) {
        return new Placement(stage, new BsonDocument("path", new BsonString(path)), "queryVector");
    }

    // -- which rule: the stage's path, an explicit field, the sole rule, or none (#753) ----------

    @Test
    public void asTheQueryVectorOfASearchStage_theRuleOfTheStagesPathIsUsed() {
        var operator = new VectorizeOperator(registryWithProviders(Map.of("p3", 3, "p5", 5)), "");
        var req = mongoRequest(TWO_RULES, new HashMap<>());

        assertEquals(5, operator.resolve(req, new BsonString("q"), queryVectorOf("$vectorScan", "bodyVector")).asArray().size());
        assertEquals(3, operator.resolve(req, new BsonString("q"), queryVectorOf("$vectorSearch", "summaryVector")).asArray().size());
    }

    @Test
    public void anExplicitField_namesTheRule_whateverTheStage() {
        var operator = new VectorizeOperator(registryWithProviders(Map.of("p3", 3, "p5", 5)), "");
        var req = mongoRequest(TWO_RULES, new HashMap<>());
        var longForm = new BsonDocument("text", new BsonString("q")).append("field", new BsonString("bodyVector"));

        assertEquals(5, operator.resolve(req, longForm, null).asArray().size());
        assertEquals(5, operator.resolve(req, longForm, queryVectorOf("$vectorScan", "summaryVector")).asArray().size());
    }

    @Test
    public void aFieldNoRuleWrites_isRefused_namingItAndTheCollectionsVectorFields() {
        var operator = new VectorizeOperator(registryWithProviders(Map.of("p3", 3, "p5", 5)), "");
        var req = mongoRequest(TWO_RULES, new HashMap<>());

        var e = assertThrows(IllegalArgumentException.class, () -> operator.resolve(req, new BsonString("q"), queryVectorOf("$vectorScan", "nope")));
        assertTrue(e.getMessage().contains("'nope'"), e.getMessage());
        assertTrue(e.getMessage().contains("summaryVector") && e.getMessage().contains("bodyVector"), e.getMessage());

        var longForm = new BsonDocument("text", new BsonString("q")).append("field", new BsonString("nope"));
        assertThrows(IllegalArgumentException.class, () -> operator.resolve(req, longForm, null));
    }

    @Test
    public void severalRulesAndNothingSayingWhich_isRefused() {
        var operator = new VectorizeOperator(registryWithProviders(Map.of("p3", 3, "p5", 5)), "");
        var req = mongoRequest(TWO_RULES, new HashMap<>());

        assertThrows(IllegalArgumentException.class, () -> operator.resolve(req, new BsonString("q")));
        // a placement that is not a search stage's queryVector does not say which either
        assertThrows(IllegalArgumentException.class, () -> operator.resolve(req, new BsonString("q"), new Placement("$addFields", new BsonDocument(), "qv")));
    }

    @Test
    public void oneRule_isUsedAsBefore_andNoRuleMeansTheDefaultProvider() {
        var operator = new VectorizeOperator(registryWithProviders(Map.of("p3", 3, "dflt", 7)), "dflt");

        var oneRule = BsonDocument.parse("{ \"vectorSearch\": { \"textField\": \"t\", \"embeddingField\": \"v\", \"provider\": \"p3\" } }");
        assertEquals(3, operator.resolve(mongoRequest(oneRule, new HashMap<>()), new BsonString("q")).asArray().size());
        assertEquals(3, operator.resolve(mongoRequest(oneRule, new HashMap<>()), new BsonString("q"), queryVectorOf("$vectorScan", "v")).asArray().size());

        // no rules: vectors written by something else, the default provider even under a search stage
        assertEquals(7, operator.resolve(mongoRequest(new BsonDocument(), new HashMap<>()), new BsonString("q"), queryVectorOf("$vectorScan", "v")).asArray().size());
        // but an explicit field on a collection with no rule is a mistake
        var longForm = new BsonDocument("text", new BsonString("q")).append("field", new BsonString("v"));
        assertThrows(IllegalArgumentException.class, () -> operator.resolve(mongoRequest(new BsonDocument(), new HashMap<>()), longForm, null));
    }

    @Test
    public void theRulesOverridesAreAttachedForTheCallOnly_andPutBackAfter() {
        var state = new HashMap<String, Object>();
        state.put(RequestOverrides.VOYAGE_MODEL, "voyage-4");
        var rules = BsonDocument.parse("""
            { "vectorSearch": [
                { "textField": "s", "embeddingField": "sv", "provider": "voyageEmbeddingProvider", "model": "voyage-law-2" },
                { "textField": "b", "embeddingField": "bv", "provider": "p5" } ] }""");
        var seenDuringEmbed = new HashMap<String, Object>();
        EmbeddingModel voyage = (texts, request) -> {
            seenDuringEmbed.put(RequestOverrides.VOYAGE_MODEL, request.attachedParam(RequestOverrides.VOYAGE_MODEL));
            seenDuringEmbed.put(RequestOverrides.VOYAGE_INPUT_TYPE, request.attachedParam(RequestOverrides.VOYAGE_INPUT_TYPE));
            return List.of(new float[2]);
        };
        var provider = mock(Provider.class);
        when(provider.get(null)).thenReturn(voyage);
        var record = mock(PluginRecord.class);
        when(record.getName()).thenReturn("voyageEmbeddingProvider");
        when(record.isEnabled()).thenReturn(true);
        when(record.getInstance()).thenReturn(provider);
        var registry = mock(PluginsRegistry.class);
        when(registry.getProviders()).thenReturn((Set) Set.of(record));

        var operator = new VectorizeOperator(registry, "");
        operator.resolve(mongoRequest(rules, state), new BsonString("q"), queryVectorOf("$vectorScan", "sv"));

        // during the call: the rule's model, and a question is a query
        assertEquals("voyage-law-2", seenDuringEmbed.get(RequestOverrides.VOYAGE_MODEL));
        assertEquals("query", seenDuringEmbed.get(RequestOverrides.VOYAGE_INPUT_TYPE));
        // after: as it was
        assertEquals("voyage-4", state.get(RequestOverrides.VOYAGE_MODEL));
        assertNull(state.get(RequestOverrides.VOYAGE_INPUT_TYPE));
        assertNull(state.get(RequestOverrides.EMBEDDING_PROVIDER));
    }

    @Test
    public void theLongFormNeedsAStringText() {
        var operator = new VectorizeOperator(registryWithProviders(Map.of("p", 1)), "p");
        assertThrows(IllegalArgumentException.class, () -> operator.resolve(null, new BsonDocument("field", new BsonString("v"))));
    }

    @Test
    public void resolve_callsConfiguredProvider_returnsVectorAsBsonArray() {
        EmbeddingModel model = (texts, request) -> List.of(new float[]{0.1f, 0.2f});
        var registry = registryWithProvider("openAIEmbeddingProvider", true, model);

        var operator = new VectorizeOperator(registry, "openAIEmbeddingProvider");
        var result = operator.resolve(null, new BsonString("hello"));

        assertEquals(2, result.asArray().size());
        assertEquals(0.1, result.asArray().get(0).asDouble().getValue(), 1e-6);
        assertEquals(0.2, result.asArray().get(1).asDouble().getValue(), 1e-6);
    }

    @Test
    public void resolve_throwsWhenArgIsNotAString() {
        var registry = registryWithProvider("p", true, (texts, request) -> List.of());
        var operator = new VectorizeOperator(registry, "p");

        assertThrows(IllegalArgumentException.class, () -> operator.resolve(null, new BsonInt32(42)));
    }

    @Test
    public void resolve_throwsWhenNoProviderConfiguredAndNoOverride() {
        var registry = mock(PluginsRegistry.class);
        var operator = new VectorizeOperator(registry, "");

        assertThrows(IllegalStateException.class, () -> operator.resolve(null, new BsonString("hello")));
    }

    @Test
    public void resolve_throwsWhenConfiguredProviderIsNotFound() {
        var registry = mock(PluginsRegistry.class);
        when(registry.getProviders()).thenReturn((Set) Set.of());

        var operator = new VectorizeOperator(registry, "missingProvider");

        assertThrows(IllegalStateException.class, () -> operator.resolve(null, new BsonString("hello")));
    }

    @Test
    public void resolve_usesRequestOverrideProviderNameOverStaticDefault() {
        EmbeddingModel model = (texts, request) -> List.of(new float[]{1.0f});
        var registry = registryWithProvider("overriddenProvider", true, model);

        var operator = new VectorizeOperator(registry, "defaultProvider");

        var req = mock(Request.class);
        when(req.attachedParam(RequestOverrides.EMBEDDING_PROVIDER)).thenReturn("overriddenProvider");

        var result = operator.resolve(req, new BsonString("hello"));

        assertEquals(1.0, result.asArray().get(0).asDouble().getValue(), 1e-6);
    }
}
