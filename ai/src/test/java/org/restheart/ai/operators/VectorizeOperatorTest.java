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
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.exchange.Request;
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

    @Test
    public void resolve_callsConfiguredProvider_returnsVectorAsBsonArray() {
        EmbeddingModel model = (texts, request) -> List.of(new float[] {0.1f, 0.2f});
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
        EmbeddingModel model = (texts, request) -> List.of(new float[] {1.0f});
        var registry = registryWithProvider("overriddenProvider", true, model);

        var operator = new VectorizeOperator(registry, "defaultProvider");

        var req = mock(Request.class);
        when(req.attachedParam(RequestOverrides.EMBEDDING_PROVIDER)).thenReturn("overriddenProvider");

        var result = operator.resolve(req, new BsonString("hello"));

        assertEquals(1.0, result.asArray().get(0).asDouble().getValue(), 1e-6);
    }
}
