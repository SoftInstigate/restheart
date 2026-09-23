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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.ai.util.CollectionEmbeddingConfig.EmbeddingRule;
import org.restheart.exchange.Request;

public class CollectionEmbeddingConfigTest {

    private static BsonDocument props(String vectorSearch) {
        return BsonDocument.parse("{ \"vectorSearch\": " + vectorSearch + " }");
    }

    private static EmbeddingRule rule(String provider, String model, Integer dimensions) {
        return new EmbeddingRule("t", "e", provider, model, dimensions);
    }

    private static String reason(String vectorSearch) {
        return CollectionEmbeddingConfig.invalidReason(BsonDocument.parse("{ \"v\": " + vectorSearch + " }").get("v"));
    }

    // -- rules: the object form is a list of one, the list form as many as it holds --------

    @Test
    public void rules_noProps_none() {
        assertTrue(CollectionEmbeddingConfig.rules(null).isEmpty());
        assertTrue(CollectionEmbeddingConfig.rules(new BsonDocument()).isEmpty());
        assertTrue(CollectionEmbeddingConfig.rules(props("\"not a rule\"")).isEmpty());
    }

    @Test
    public void rules_objectForm_isAListOfOne() {
        var rules = CollectionEmbeddingConfig.rules(props("{ \"textField\": \"description\", \"embeddingField\": \"embedding\", \"model\": \"voyage-law-2\", \"dimensions\": 1024 }"));
        assertEquals(1, rules.size());
        assertEquals(new EmbeddingRule("description", "embedding", null, "voyage-law-2", 1024), rules.get(0));
        assertEquals(rules.get(0), CollectionEmbeddingConfig.soleRule(props("{ \"textField\": \"description\", \"embeddingField\": \"embedding\", \"model\": \"voyage-law-2\", \"dimensions\": 1024 }")).orElseThrow());
    }

    @Test
    public void rules_listForm_inDeclaredOrder() {
        var rules = CollectionEmbeddingConfig.rules(props("""
            [ { "textField": "summary", "embeddingField": "summaryVector", "provider": "voyageEmbeddingProvider", "model": "voyage-law-2" },
              { "textField": "body", "embeddingField": "bodyVector", "provider": "voyageContextualEmbeddingProvider", "model": "voyage-context-4", "dimensions": 512 } ]"""));
        assertEquals(2, rules.size());
        assertEquals(new EmbeddingRule("summary", "summaryVector", "voyageEmbeddingProvider", "voyage-law-2", null), rules.get(0));
        assertEquals(new EmbeddingRule("body", "bodyVector", "voyageContextualEmbeddingProvider", "voyage-context-4", 512), rules.get(1));
        assertTrue(CollectionEmbeddingConfig.soleRule(props("[ { \"textField\": \"a\", \"embeddingField\": \"av\" }, { \"textField\": \"b\", \"embeddingField\": \"bv\" } ]")).isEmpty());
    }

    @Test
    public void rules_skipsWhatLacksATextOrAnEmbeddingField() {
        var rules = CollectionEmbeddingConfig.rules(props("[ { \"textField\": \"a\" }, { \"embeddingField\": \"bv\" }, 7, { \"textField\": \"c\", \"embeddingField\": \"cv\" } ]"));
        assertEquals(1, rules.size());
        assertEquals("cv", rules.get(0).embeddingField());
        assertTrue(CollectionEmbeddingConfig.rules(props("{ \"textField\": \"description\" }")).isEmpty());
    }

    // -- invalidReason: refused when written, naming the rule --------------------------------

    @Test
    public void invalidReason_validForms_null() {
        assertNull(reason("null"));
        assertNull(reason("{ \"textField\": \"t\", \"embeddingField\": \"e\" }"));
        assertNull(reason("[ { \"textField\": \"t\", \"embeddingField\": \"e\", \"provider\": \"p\", \"model\": \"m\", \"dimensions\": 3 }, { \"textField\": \"t\", \"embeddingField\": \"f\" } ]"));
        assertNull(reason("[]"));
    }

    @Test
    public void invalidReason_neitherObjectNorList() {
        assertTrue(reason("\"x\"").startsWith("vectorSearch must be an object"));
        assertEquals("vectorSearch[1] must be an object with textField and embeddingField", reason("[ { \"textField\": \"t\", \"embeddingField\": \"e\" }, 3 ]"));
    }

    @Test
    public void invalidReason_ruleMissingAField_namesTheRuleAndTheField() {
        assertEquals("vectorSearch needs a non-blank string textField", reason("{ \"embeddingField\": \"e\" }"));
        assertEquals("vectorSearch[1] needs a non-blank string embeddingField", reason("[ { \"textField\": \"t\", \"embeddingField\": \"e\" }, { \"textField\": \"t\", \"embeddingField\": \" \" } ]"));
    }

    @Test
    public void invalidReason_twoRulesOnOneVectorField() {
        assertEquals("vectorSearch[1] writes its vector to 'e', as vectorSearch[0] already does: one rule per vector field",
                reason("[ { \"textField\": \"a\", \"embeddingField\": \"e\" }, { \"textField\": \"b\", \"embeddingField\": \"e\" } ]"));
    }

    @Test
    public void invalidReason_optionalFieldsOfTheWrongKind() {
        assertEquals("vectorSearch (embeddingField 'e'): model must be a non-blank string", reason("{ \"textField\": \"t\", \"embeddingField\": \"e\", \"model\": 3 }"));
        assertEquals("vectorSearch (embeddingField 'e'): provider must be a non-blank string", reason("{ \"textField\": \"t\", \"embeddingField\": \"e\", \"provider\": \"\" }"));
        assertEquals("vectorSearch (embeddingField 'e'): dimensions must be a positive number", reason("{ \"textField\": \"t\", \"embeddingField\": \"e\", \"dimensions\": 0 }"));
        assertNull(reason("{ \"textField\": \"t\", \"embeddingField\": \"e\", \"model\": null }"));
    }

    // -- overridesFor: a rule as the request overrides the providers read --------------------

    @Test
    public void textAndEmbeddingFieldOnly_nothing() {
        assertTrue(CollectionEmbeddingConfig.overridesFor(rule(null, null, null), "voyageEmbeddingProvider").isEmpty());
        assertTrue(CollectionEmbeddingConfig.overridesFor(null, "voyageEmbeddingProvider").isEmpty());
    }

    @Test
    public void model_keyedByTheProviderInForce() {
        var o = CollectionEmbeddingConfig.overridesFor(rule(null, "voyage-law-2", null), "voyageEmbeddingProvider");
        assertEquals("voyage-law-2", o.get(RequestOverrides.VOYAGE_MODEL));
        assertFalse(o.containsKey(RequestOverrides.EMBEDDING_PROVIDER));

        o = CollectionEmbeddingConfig.overridesFor(rule(null, "text-embedding-3-large", null), "openAIEmbeddingProvider");
        assertEquals("text-embedding-3-large", o.get(RequestOverrides.OPENAI_MODEL));
    }

    @Test
    public void provider_switchesTheProviderAndKeysTheModelByIt() {
        var o = CollectionEmbeddingConfig.overridesFor(rule("voyageContextualEmbeddingProvider", "voyage-context-4", 512), "openAIEmbeddingProvider");
        assertEquals("voyageContextualEmbeddingProvider", o.get(RequestOverrides.EMBEDDING_PROVIDER));
        assertEquals("voyage-context-4", o.get(RequestOverrides.VOYAGE_CONTEXTUAL_MODEL));
        assertEquals(512, o.get(RequestOverrides.VOYAGE_CONTEXTUAL_OUTPUT_DIMENSION));
        assertFalse(o.containsKey(RequestOverrides.OPENAI_MODEL));
    }

    @Test
    public void dimensions_onlyWhenPositiveAndTheProviderTakesThem() {
        assertFalse(CollectionEmbeddingConfig.overridesFor(rule(null, null, 0), "voyageEmbeddingProvider").containsKey(RequestOverrides.VOYAGE_OUTPUT_DIMENSION));
        assertTrue(CollectionEmbeddingConfig.overridesFor(rule(null, null, 256), "ollamaEmbeddingProvider").isEmpty());
        assertEquals(256, CollectionEmbeddingConfig.overridesFor(rule(null, null, 256), "openAIEmbeddingProvider").get(RequestOverrides.OPENAI_DIMENSIONS));
    }

    @Test
    public void unknownProvider_modelIgnored() {
        assertTrue(CollectionEmbeddingConfig.overridesFor(rule(null, "x", null), "someoneElsesProvider").isEmpty());
        assertTrue(CollectionEmbeddingConfig.overridesFor(rule(null, "x", null), "").isEmpty());
    }

    // -- attach / restore: the request as it was, for the next rule --------------------------

    @Test
    public void attach_putsEachOverrideOnTheRequest_andReturnsWhatItReplaced() {
        var req = mock(Request.class);
        when(req.attachedParam(anyString())).thenReturn(null);
        when(req.attachedParam(RequestOverrides.VOYAGE_MODEL)).thenReturn("voyage-4");

        var previous = CollectionEmbeddingConfig.attach(req, rule(null, "voyage-code-4", 1024), "voyageEmbeddingProvider");

        verify(req).attachParam(RequestOverrides.VOYAGE_MODEL, "voyage-code-4");
        verify(req).attachParam(RequestOverrides.VOYAGE_OUTPUT_DIMENSION, 1024);
        assertEquals("voyage-4", previous.get(RequestOverrides.VOYAGE_MODEL));
        assertTrue(previous.containsKey(RequestOverrides.VOYAGE_OUTPUT_DIMENSION));
        assertNull(previous.get(RequestOverrides.VOYAGE_OUTPUT_DIMENSION));
    }

    @Test
    public void attach_readsTheProviderAlreadyOverriddenOnTheRequest() {
        var req = mock(Request.class);
        when(req.attachedParam(RequestOverrides.EMBEDDING_PROVIDER)).thenReturn("openAIEmbeddingProvider");

        CollectionEmbeddingConfig.attach(req, rule(null, "text-embedding-3-small", null), "voyageEmbeddingProvider");

        verify(req).attachParam(RequestOverrides.OPENAI_MODEL, "text-embedding-3-small");
    }

    @Test
    public void restore_putsBackEveryReplacedValue_nullIncluded() {
        var req = mock(Request.class);
        var previous = new java.util.LinkedHashMap<String, Object>();
        previous.put(RequestOverrides.EMBEDDING_PROVIDER, "openAIEmbeddingProvider");
        previous.put(RequestOverrides.VOYAGE_MODEL, null);

        CollectionEmbeddingConfig.restore(req, previous);

        verify(req).attachParam(RequestOverrides.EMBEDDING_PROVIDER, "openAIEmbeddingProvider");
        verify(req).attachParam(RequestOverrides.VOYAGE_MODEL, null);
    }

    @Test
    public void attach_thenRestore_isANoOpOnTheRequestState() {
        // the real map: a rule with a provider, a model and dimensions, applied and put back
        var state = new java.util.HashMap<String, Object>(Map.of(RequestOverrides.VOYAGE_MODEL, "voyage-4"));
        var req = mock(Request.class);
        when(req.attachedParam(anyString())).thenAnswer(inv -> state.get(inv.getArgument(0, String.class)));
        org.mockito.Mockito.doAnswer(inv -> state.put(inv.getArgument(0, String.class), inv.getArgument(1))).when(req).attachParam(anyString(), org.mockito.ArgumentMatchers.any());

        var previous = CollectionEmbeddingConfig.attach(req, rule("voyageEmbeddingProvider", "voyage-law-2", 1024), "openAIEmbeddingProvider");
        assertEquals("voyage-law-2", state.get(RequestOverrides.VOYAGE_MODEL));
        assertEquals("voyageEmbeddingProvider", state.get(RequestOverrides.EMBEDDING_PROVIDER));

        CollectionEmbeddingConfig.restore(req, previous);
        assertEquals("voyage-4", state.get(RequestOverrides.VOYAGE_MODEL));
        assertNull(state.get(RequestOverrides.EMBEDDING_PROVIDER));
        assertNull(state.get(RequestOverrides.VOYAGE_OUTPUT_DIMENSION));
    }
}
