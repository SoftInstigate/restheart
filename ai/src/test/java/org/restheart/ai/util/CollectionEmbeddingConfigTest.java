/*-
 * ========================LICENSE_START=================================
 * restheart-ai
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
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.ai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;

public class CollectionEmbeddingConfigTest {

    private static BsonDocument props(String vectorSearch) {
        return BsonDocument.parse("{ \"vectorSearch\": " + vectorSearch + " }");
    }

    @Test
    public void noProps_nothing() {
        assertTrue(CollectionEmbeddingConfig.overridesFor(null, "voyageEmbeddingProvider").isEmpty());
        assertTrue(CollectionEmbeddingConfig.overridesFor(new BsonDocument(), "voyageEmbeddingProvider").isEmpty());
    }

    @Test
    public void textAndEmbeddingFieldOnly_nothing() {
        var o = CollectionEmbeddingConfig.overridesFor(props("{ \"textField\": \"description\", \"embeddingField\": \"embedding\" }"), "voyageEmbeddingProvider");
        assertTrue(o.isEmpty());
    }

    @Test
    public void model_keyedByTheProviderInForce() {
        var o = CollectionEmbeddingConfig.overridesFor(props("{ \"textField\": \"t\", \"embeddingField\": \"e\", \"model\": \"voyage-law-2\" }"), "voyageEmbeddingProvider");
        assertEquals("voyage-law-2", o.get(RequestOverrides.VOYAGE_MODEL));
        assertFalse(o.containsKey(RequestOverrides.EMBEDDING_PROVIDER));

        o = CollectionEmbeddingConfig.overridesFor(props("{ \"model\": \"text-embedding-3-large\" }"), "openAIEmbeddingProvider");
        assertEquals("text-embedding-3-large", o.get(RequestOverrides.OPENAI_MODEL));
    }

    @Test
    public void provider_switchesTheProviderAndKeysTheModelByIt() {
        var o = CollectionEmbeddingConfig.overridesFor(
                props("{ \"provider\": \"voyageContextualEmbeddingProvider\", \"model\": \"voyage-context-4\", \"dimensions\": 512 }"),
                "openAIEmbeddingProvider");
        assertEquals("voyageContextualEmbeddingProvider", o.get(RequestOverrides.EMBEDDING_PROVIDER));
        assertEquals("voyage-context-4", o.get(RequestOverrides.VOYAGE_CONTEXTUAL_MODEL));
        assertEquals(512, o.get(RequestOverrides.VOYAGE_CONTEXTUAL_OUTPUT_DIMENSION));
        assertFalse(o.containsKey(RequestOverrides.OPENAI_MODEL));
    }

    @Test
    public void dimensions_onlyWhenPositiveAndTheProviderTakesThem() {
        var o = CollectionEmbeddingConfig.overridesFor(props("{ \"dimensions\": 0 }"), "voyageEmbeddingProvider");
        assertFalse(o.containsKey(RequestOverrides.VOYAGE_OUTPUT_DIMENSION));

        o = CollectionEmbeddingConfig.overridesFor(props("{ \"dimensions\": 256 }"), "ollamaEmbeddingProvider");
        assertTrue(o.isEmpty());

        o = CollectionEmbeddingConfig.overridesFor(props("{ \"dimensions\": 256 }"), "openAIEmbeddingProvider");
        assertEquals(256, o.get(RequestOverrides.OPENAI_DIMENSIONS));
    }

    @Test
    public void unknownProvider_modelIgnored() {
        var o = CollectionEmbeddingConfig.overridesFor(props("{ \"model\": \"x\" }"), "someoneElsesProvider");
        assertTrue(o.isEmpty());
        o = CollectionEmbeddingConfig.overridesFor(props("{ \"model\": \"x\" }"), "");
        assertTrue(o.isEmpty());
    }

    @Test
    public void attach_putsEachOverrideOnTheRequest() {
        var req = mock(Request.class);
        when(req.attachedParam(anyString())).thenReturn(null);

        CollectionEmbeddingConfig.attach(req, props("{ \"model\": \"voyage-code-4\", \"dimensions\": 1024 }"), "voyageEmbeddingProvider");

        verify(req).attachParam(RequestOverrides.VOYAGE_MODEL, "voyage-code-4");
        verify(req).attachParam(RequestOverrides.VOYAGE_OUTPUT_DIMENSION, 1024);
    }

    @Test
    public void attach_readsTheProviderAlreadyOverriddenOnTheRequest() {
        var req = mock(Request.class);
        when(req.attachedParam(RequestOverrides.EMBEDDING_PROVIDER)).thenReturn("openAIEmbeddingProvider");

        CollectionEmbeddingConfig.attach(req, props("{ \"model\": \"text-embedding-3-small\" }"), "voyageEmbeddingProvider");

        verify(req).attachParam(RequestOverrides.OPENAI_MODEL, "text-embedding-3-small");
    }
}
