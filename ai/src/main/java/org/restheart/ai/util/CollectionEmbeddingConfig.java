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

import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.Request;

/**
 * The embedding model of a collection, read from its {@code vectorSearch} metadata.
 *
 * <p>The model is a property of the vector field: the vectors of a field and those of the
 * questions searched against it must come from the same model, and a service may want
 * {@code voyage-law-2} on {@code /legal} and {@code voyage-code-4} on {@code /src}. The static
 * configuration of the providers, and the {@code override-ai-*} parameters a request carries,
 * give the default; a collection that names its own wins.
 *
 * <pre>{@code
 * { "vectorSearch": {
 *     "textField": "description", "embeddingField": "embedding",
 *     "provider": "voyageEmbeddingProvider",   // optional: the default provider otherwise
 *     "model": "voyage-law-2",                 // optional: the provider's default otherwise
 *     "dimensions": 1024                       // optional: the model's default otherwise
 * } }
 * }</pre>
 *
 * <p>{@link #attach} turns those into the same request overrides the providers already read,
 * so nothing else changes: {@code autoEmbeddingInterceptor} attaches them before embedding a
 * write, {@code $vectorize} before embedding a question.
 */
public final class CollectionEmbeddingConfig {
    static final String VECTOR_SEARCH = "vectorSearch";
    static final String PROVIDER = "provider";
    static final String MODEL = "model";
    static final String DIMENSIONS = "dimensions";

    private CollectionEmbeddingConfig() {
    }

    /**
     * Attaches to the request the overrides the collection's metadata calls for; nothing when
     * the metadata names none.
     *
     * @param request the request being served
     * @param collectionProps the collection's properties, may be null
     * @param defaultProvider the provider the request would use otherwise
     */
    public static void attach(Request<?> request, BsonDocument collectionProps, String defaultProvider) {
        var current = RequestOverrides.str(request, RequestOverrides.EMBEDDING_PROVIDER, defaultProvider);
        overridesFor(collectionProps, current).forEach(request::attachParam);
    }

    /**
     * The overrides a collection's metadata calls for, keyed as {@link RequestOverrides} names
     * them. Pure, so it can be tested without an exchange.
     *
     * @param collectionProps the collection's properties, may be null
     * @param effectiveProvider the provider in force before the metadata is read, may be blank
     * @return the parameters to attach, in the order they were derived; empty when there are none
     */
    public static Map<String, Object> overridesFor(BsonDocument collectionProps, String effectiveProvider) {
        var overrides = new LinkedHashMap<String, Object>();

        var vs = collectionProps == null ? null : collectionProps.get(VECTOR_SEARCH);
        if (vs == null || !vs.isDocument()) {
            return overrides;
        }

        var provider = str(vs.asDocument().get(PROVIDER));
        if (provider != null) {
            overrides.put(RequestOverrides.EMBEDDING_PROVIDER, provider);
        }
        var inForce = provider != null ? provider : effectiveProvider;

        var model = str(vs.asDocument().get(MODEL));
        var modelKey = modelKeyOf(inForce);
        if (model != null && modelKey != null) {
            overrides.put(modelKey, model);
        }

        var dimensions = vs.asDocument().get(DIMENSIONS);
        var dimensionsKey = dimensionsKeyOf(inForce);
        if (dimensions != null && dimensions.isNumber() && dimensionsKey != null) {
            var n = dimensions.asNumber().intValue();
            if (n > 0) {
                overrides.put(dimensionsKey, n);
            }
        }

        return overrides;
    }

    /** The override that sets the model of a provider; null for a provider that has none, or an unknown one. */
    static String modelKeyOf(String provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case "voyageEmbeddingProvider" -> RequestOverrides.VOYAGE_MODEL;
            case "voyageContextualEmbeddingProvider" -> RequestOverrides.VOYAGE_CONTEXTUAL_MODEL;
            case "openAIEmbeddingProvider" -> RequestOverrides.OPENAI_MODEL;
            case "ollamaEmbeddingProvider" -> RequestOverrides.OLLAMA_MODEL;
            default -> null;
        };
    }

    /** The override that sets the vector's length; null for a provider that takes none. */
    static String dimensionsKeyOf(String provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case "voyageEmbeddingProvider" -> RequestOverrides.VOYAGE_OUTPUT_DIMENSION;
            case "voyageContextualEmbeddingProvider" -> RequestOverrides.VOYAGE_CONTEXTUAL_OUTPUT_DIMENSION;
            case "openAIEmbeddingProvider" -> RequestOverrides.OPENAI_DIMENSIONS;
            default -> null;
        };
    }

    private static String str(BsonValue v) {
        return v != null && v.isString() && !v.asString().getValue().isBlank() ? v.asString().getValue() : null;
    }
}
