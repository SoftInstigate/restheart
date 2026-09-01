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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonArray;
import org.bson.BsonDouble;
import org.bson.BsonValue;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.exchange.Request;
import org.restheart.mongodb.utils.CustomOperator;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.ai.EmbeddingModel;

/**
 * Registers the {@code $vectorize} custom operator: converts a text argument into an
 * embedding vector inline in an aggregation pipeline, via whichever
 * {@code Provider<EmbeddingModel>} is configured — the same one
 * {@code autoEmbeddingInterceptor} uses (shared {@link RequestOverrides#EMBEDDING_PROVIDER}
 * override), since a tenant's choice of embedding vendor is naturally the same for both
 * writing embeddings and querying with them.
 *
 * <pre>{@code
 * PUT /mydb/articles/_indexes/article_vectors
 * { "type": "vectorSearch",
 *   "definition": { "fields": [
 *     { "type": "vector", "path": "embedding", "numDimensions": 1536, "similarity": "cosine" }
 *   ]}}
 *
 * PUT /mydb/articles
 * { "aggrs": [{ "uri": "search", "type": "pipeline", "stages": [
 *     { "$vectorSearch": {
 *         "index": "article_vectors",
 *         "path": "embedding",
 *         "queryVector": { "$vectorize": { "$var": "query" } },
 *         "numCandidates": 100,
 *         "limit": 10
 *     }},
 *     { "$project": { "embedding": 0 } }
 * ]}]}
 *
 * GET /mydb/articles/_aggrs/search?avars={"query":"machine learning for NLP"}
 * }</pre>
 *
 * <p>Not itself a RESTHeart plugin — a plain {@link CustomOperator} implementation
 * constructed and registered by {@link VectorizeOperatorInitializer}, exactly the
 * pattern {@code restheart-stripe}'s {@code SubscriptionVarResolver} uses for the
 * analogous {@code VarResolver} SPI.
 *
 * @see VectorizeOperatorInitializer
 */
public class VectorizeOperator implements CustomOperator {
    private final PluginsRegistry registry;
    private final String defaultProviderName;

    // resolved lazily and cached per provider name, mirroring AutoEmbeddingInterceptor
    private final Map<String, EmbeddingModel> resolvedModels = new ConcurrentHashMap<>();

    public VectorizeOperator(PluginsRegistry registry, String defaultProviderName) {
        this.registry = registry;
        this.defaultProviderName = defaultProviderName == null ? "" : defaultProviderName;
    }

    @Override
    public String name() {
        return "vectorize";
    }

    @Override
    public BsonValue resolve(Request<?> request, BsonValue arg) {
        if (arg == null || !arg.isString()) {
            throw new IllegalArgumentException("$vectorize requires a string argument, got: " + arg);
        }
        var text = arg.asString().getValue();

        var providerName = RequestOverrides.str(request, RequestOverrides.EMBEDDING_PROVIDER, defaultProviderName);
        if (providerName.isBlank()) {
            throw new IllegalStateException(
                "$vectorize used but no embedding-provider is configured (vectorizeOperator or " +
                RequestOverrides.EMBEDDING_PROVIDER + ")");
        }

        var model = resolveEmbeddingModel(providerName);

        List<float[]> vectors;
        try {
            vectors = model.embed(List.of(text), request);
        } catch (Exception e) {
            throw new RuntimeException("$vectorize: embedding call to '" + providerName + "' failed: " + e.getMessage(), e);
        }

        if (vectors.isEmpty() || vectors.get(0) == null) {
            throw new IllegalStateException("$vectorize: provider '" + providerName + "' returned no embedding");
        }

        var result = new BsonArray();
        for (var f : vectors.get(0)) {
            result.add(new BsonDouble(f));
        }
        return result;
    }

    private EmbeddingModel resolveEmbeddingModel(String providerName) {
        var cached = resolvedModels.get(providerName);
        if (cached != null) {
            return cached;
        }

        var providerRecord = registry.getProviders().stream()
            .filter(p -> providerName.equals(p.getName()))
            .findFirst()
            .orElse(null);

        if (providerRecord == null || !providerRecord.isEnabled()) {
            throw new IllegalStateException("$vectorize: embedding provider '" + providerName + "' not found or not enabled");
        }

        var provided = providerRecord.getInstance().get(null);
        if (!(provided instanceof EmbeddingModel model)) {
            throw new IllegalStateException("$vectorize: provider '" + providerName + "' does not supply an EmbeddingModel");
        }

        resolvedModels.put(providerName, model);
        return model;
    }
}
