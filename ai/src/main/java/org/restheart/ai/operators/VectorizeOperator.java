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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonValue;
import org.restheart.ai.util.PluginModelResolver;
import org.restheart.ai.util.CollectionEmbeddingConfig;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.exchange.MongoRequest;
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
 * <p>The question is embedded with the model of the vectors it is searched against: as the
 * {@code queryVector} of a {@code $vectorSearch} or {@code $vectorScan} stage, {@code $vectorize}
 * reads the stage's {@code path} and uses the collection's embedding rule for that field (see
 * {@link CollectionEmbeddingConfig}); elsewhere, {@code {"$vectorize": {"text": ..., "field":
 * "bodyVector"}}} names the field. See {@link #resolve(Request, BsonValue, Placement)}.
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
        return resolve(request, arg, null);
    }

    /**
     * Embeds the text with the model of the vector field it is for.
     *
     * <p>Which field, in order (restheart#753): the {@code field} of the long form
     * {@code {"$vectorize": {"text": ..., "field": "bodyVector"}}}; else, when this is the
     * {@code queryVector} of a {@code $vectorSearch} or {@code $vectorScan} stage, that stage's
     * {@code path}; else the collection's sole rule, when it has one. A field that no rule writes
     * is refused: embedding a question with a model its vectors may not match would make the
     * search silently wrong. A collection with no rules embeds with the deployment's default
     * provider, as it always did: its vectors were written by something else.
     */
    @Override
    public BsonValue resolve(Request<?> request, BsonValue arg, Placement placement) {
        String text;
        String field = null;
        if (arg != null && arg.isString()) {
            text = arg.asString().getValue();
        } else if (arg != null && arg.isDocument() && arg.asDocument().get("text") != null && arg.asDocument().get("text").isString()) {
            text = arg.asDocument().getString("text").getValue();
            var f = arg.asDocument().get("field");
            field = f != null && f.isString() && !f.asString().getValue().isBlank() ? f.asString().getValue() : null;
        } else {
            throw new IllegalArgumentException("$vectorize takes a string, or {\"text\": <string>, \"field\": <vector field>}, got: " + arg);
        }

        // the question is embedded with the model of the vectors it is searched against, when the
        // collection names one: the same overrides autoEmbeddingInterceptor attaches on a write,
        // put back afterwards so another $vectorize in the same pipeline starts clean
        var previous = new LinkedHashMap<String, Object>();
        if (request instanceof MongoRequest mongoRequest) {
            ruleFor(mongoRequest.getCollectionProps(), field, placement)
                    .ifPresent(rule -> previous.putAll(CollectionEmbeddingConfig.attach(mongoRequest, rule, defaultProviderName)));
        }

        try {
            var providerName = RequestOverrides.str(request, RequestOverrides.EMBEDDING_PROVIDER, defaultProviderName);
            if (providerName.isBlank()) {
                throw new IllegalStateException(
                        "$vectorize used but no embedding-provider is configured (vectorizeOperator or " +
                                RequestOverrides.EMBEDDING_PROVIDER + ")");
            }

            // a question is a query: Voyage embeds it as such unless the deployment said otherwise
            var inputTypeKey = queryInputTypeKey(providerName);
            if (request != null && inputTypeKey != null && RequestOverrides.str(request, inputTypeKey, "").isBlank()) {
                previous.putIfAbsent(inputTypeKey, request.attachedParam(inputTypeKey));
                request.attachParam(inputTypeKey, "query");
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
        } finally {
            if (request != null) {
                CollectionEmbeddingConfig.restore(request, previous);
            }
        }
    }

    /**
     * The rule to embed with, or empty for the deployment's default provider.
     *
     * @throws IllegalArgumentException when a field is asked for that no rule writes, or when the
     *         collection has several rules and nothing says which one
     */
    static Optional<CollectionEmbeddingConfig.EmbeddingRule> ruleFor(BsonDocument collectionProps, String field, Placement placement) {
        var rules = CollectionEmbeddingConfig.rules(collectionProps);

        var wanted = field != null ? field : pathOfSearchStage(placement);
        if (wanted != null) {
            if (rules.isEmpty()) {
                if (field != null) {
                    throw new IllegalArgumentException("$vectorize: field '" + field + "' names a vector field, but the collection declares no embedding rule");
                }
                return Optional.empty(); // vectors written by something else: the default provider, as always
            }
            var rule = CollectionEmbeddingConfig.ruleFor(collectionProps, wanted);
            if (rule.isEmpty()) {
                throw new IllegalArgumentException("$vectorize: no embedding rule writes to '" + wanted
                        + "'; the collection's vector fields are " + vectorFields(rules));
            }
            return rule;
        }

        if (rules.size() > 1) {
            throw new IllegalArgumentException("$vectorize: the collection has " + rules.size() + " embedding rules, on "
                    + vectorFields(rules) + ", and this $vectorize does not say which vector field it is for: "
                    + "use it as the queryVector of a $vectorSearch or $vectorScan stage, or the form {\"text\": ..., \"field\": ...}");
        }
        return rules.isEmpty() ? Optional.empty() : Optional.of(rules.get(0));
    }

    /** The {@code path} of the {@code $vectorSearch} or {@code $vectorScan} stage this is the {@code queryVector} of, else null. */
    private static String pathOfSearchStage(Placement placement) {
        if (placement == null || !"queryVector".equals(placement.key())) {
            return null;
        }
        return "$vectorSearch".equals(placement.operator()) || "$vectorScan".equals(placement.operator())
                ? placement.argString("path")
                : null;
    }

    private static List<String> vectorFields(List<CollectionEmbeddingConfig.EmbeddingRule> rules) {
        return rules.stream().map(CollectionEmbeddingConfig.EmbeddingRule::embeddingField).toList();
    }

    /** The override that sets the input type of a provider that distinguishes queries from documents; null for the others. */
    static String queryInputTypeKey(String providerName) {
        return switch (providerName) {
            case "voyageEmbeddingProvider" -> RequestOverrides.VOYAGE_INPUT_TYPE;
            case "voyageContextualEmbeddingProvider" -> RequestOverrides.VOYAGE_CONTEXTUAL_INPUT_TYPE;
            default -> null;
        };
    }

    private EmbeddingModel resolveEmbeddingModel(String providerName) {
        return PluginModelResolver.resolve(registry, resolvedModels, providerName, EmbeddingModel.class)
                .orElseThrow(() -> new IllegalStateException(
                        "$vectorize: embedding provider '" + providerName + "' not found, not enabled, "
                                + "or does not supply an EmbeddingModel"));
    }
}
