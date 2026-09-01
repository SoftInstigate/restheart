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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonValue;
import org.restheart.ai.util.PluginModelResolver;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.ai.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automatically generates an embedding vector for documents written to collections
 * that declare {@code vectorSearch.textField} / {@code vectorSearch.embeddingField}
 * metadata, using whichever {@code Provider<EmbeddingModel>} is named in this
 * interceptor's own {@code embedding-provider} configuration (e.g.
 * {@code openAIEmbeddingProvider}, {@code voyageEmbeddingProvider},
 * {@code ollamaEmbeddingProvider}).
 *
 * <p>This is the write-side counterpart to Phase 1's MongoDB {@code autoEmbed}
 * index type: where {@code autoEmbed} lets MongoDB itself generate embeddings,
 * this interceptor generates them in RESTHeart via a pluggable provider — for
 * deployments that don't have {@code autoEmbed} available or want a specific
 * embedding model/vendor.
 *
 * <h2>Configuration (plugins-args)</h2>
 * <pre>{@code
 * plugins-args:
 *   autoEmbeddingInterceptor:
 *     enabled: false                          # must be explicitly enabled
 *     embedding-provider: openAIEmbeddingProvider   # name of a configured Provider<EmbeddingModel>
 * }</pre>
 *
 * <h2>Enable on a collection</h2>
 * <pre>{@code
 * PATCH /mydb/articles
 * { "vectorSearch": { "textField": "description", "embeddingField": "embedding" } }
 * }</pre>
 *
 * <p>Documents written with {@code POST}/{@code PUT}/{@code PATCH} (single document
 * or a bulk array) that have a string value in {@code textField} get an
 * {@code embeddingField} array appended before the write reaches MongoDB. Documents
 * without a string {@code textField} value are left untouched. Multiple documents in
 * one request are embedded in a single batched call to the provider.
 *
 * <h2>Multi-tenant</h2>
 * <p>Per request, a deployment's tenant-config interceptor may attach
 * {@link RequestOverrides#EMBEDDING_PROVIDER} to route that request to a different
 * {@code Provider<EmbeddingModel>} than this interceptor's own static
 * {@code embedding-provider} — a tenant with no static configuration at all can be
 * enabled purely through this override. The named provider's own implementation is
 * responsible for resolving any further per-request overrides it defines (API key,
 * model, base URL) — this interceptor only resolves and passes through <em>which</em>
 * provider to use, plus the {@link org.restheart.exchange.Request} itself so the
 * provider can look up its own overrides.
 */
@RegisterPlugin(
    name = "autoEmbeddingInterceptor",
    description = "Automatically generates embeddings for documents on write, via a configured EmbeddingModel provider",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    requiresContent = true,
    enabledByDefault = false
)
public class AutoEmbeddingInterceptor implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoEmbeddingInterceptor.class);

    static final String VECTOR_SEARCH_ELEMENT_NAME = "vectorSearch";

    @Inject("config")
    private Map<String, Object> config;

    @Inject("registry")
    private PluginsRegistry registry;

    private String defaultProviderName;
    private boolean enabled = false;

    // resolved lazily (once all plugins have finished @OnInit) and cached per provider
    // name, since override-ai-embedding-provider can name a different provider per request
    private final Map<String, EmbeddingModel> resolvedModels = new ConcurrentHashMap<>();

    @OnInit
    public void init() {
        this.defaultProviderName = argOrDefault(config, "embedding-provider", "");
        this.enabled = defaultProviderName != null && !defaultProviderName.isBlank();

        if (!enabled) {
            LOGGER.warn("autoEmbeddingInterceptor: no embedding-provider configured, interceptor is a no-op "
                + "unless every request overrides it via {}", RequestOverrides.EMBEDDING_PROVIDER);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
            && request.isWriteDocument()
            && !response.isInError()
            && !effectiveProviderName(request).isBlank()
            && findVectorSearchConfig(request.getCollectionProps()) != null;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var vsConfig = findVectorSearchConfig(request.getCollectionProps());
        if (vsConfig == null) {
            return;
        }

        var content = request.getContent();
        var docs = asDocumentList(content);
        if (docs.isEmpty()) {
            return;
        }

        // findVectorSearchConfig() already validated both fields are present and are strings
        var textField = vsConfig.get("textField").asString().getValue();
        var embeddingField = vsConfig.get("embeddingField").asString().getValue();

        var targets = new ArrayList<BsonDocument>();
        var texts = new ArrayList<String>();
        collectEmbeddableTexts(docs, textField, targets, texts);

        if (texts.isEmpty()) {
            return;
        }

        var providerName = effectiveProviderName(request);
        var model = resolveEmbeddingModel(providerName);
        if (model == null) {
            return;
        }

        List<float[]> vectors;
        try {
            vectors = model.embed(texts, request);
        } catch (Exception e) {
            LOGGER.error("autoEmbeddingInterceptor: failed to generate embeddings via '{}': {}",
                providerName, e.getMessage(), e);
            response.addWarning("auto-embedding failed: " + e.getMessage());
            return;
        }

        applyEmbeddings(targets, vectors, embeddingField);
    }

    /**
     * The embedding provider name to use for this request: the per-request
     * {@link RequestOverrides#EMBEDDING_PROVIDER} override if attached, else this
     * interceptor's own static {@code embedding-provider} configuration. Note this
     * means a tenant with no static configuration at all can still use this
     * interceptor purely via a per-request override — {@link #enabled} does not
     * gate this, only the (unused-if-overridden) static default does.
     */
    private String effectiveProviderName(MongoRequest request) {
        return RequestOverrides.str(request, RequestOverrides.EMBEDDING_PROVIDER, defaultProviderName);
    }

    // -------------------------------------------------------------------------
    // Pure helpers below take already-extracted BSON values (not MongoRequest/
    // MongoResponse) so they can be unit-tested without constructing an exchange.

    /**
     * Extracts the {@code vectorSearch} block from collection metadata, or
     * {@code null} if the collection doesn't declare a valid one (must have both
     * {@code textField} and {@code embeddingField} as strings).
     */
    static BsonDocument findVectorSearchConfig(BsonDocument collProps) {
        if (collProps == null) {
            return null;
        }

        var vs = collProps.get(VECTOR_SEARCH_ELEMENT_NAME);
        if (vs == null || !vs.isDocument()) {
            return null;
        }

        var doc = vs.asDocument();
        var textField = doc.get("textField");
        var embeddingField = doc.get("embeddingField");
        if (textField == null || !textField.isString() || embeddingField == null || !embeddingField.isString()) {
            return null;
        }

        return doc;
    }

    /**
     * Normalizes a request body into a list of documents: a single document
     * becomes a one-element list, an array is filtered to its document elements,
     * anything else yields an empty list.
     */
    static List<BsonDocument> asDocumentList(BsonValue content) {
        if (content == null) {
            return List.of();
        }
        if (content.isDocument()) {
            return List.of(content.asDocument());
        }
        if (content.isArray()) {
            var docs = new ArrayList<BsonDocument>();
            for (var item : content.asArray()) {
                if (item.isDocument()) {
                    docs.add(item.asDocument());
                }
            }
            return docs;
        }
        return List.of();
    }

    /**
     * Fills {@code targets}/{@code texts} (in matching order) with the documents
     * that have a string value in {@code textField}, and that value. Documents
     * without a string {@code textField} are skipped — they keep their original
     * content, just without an embedding.
     */
    static void collectEmbeddableTexts(List<BsonDocument> docs, String textField,
            List<BsonDocument> targets, List<String> texts) {
        for (var doc : docs) {
            var tv = doc.get(textField);
            if (tv != null && tv.isString()) {
                targets.add(doc);
                texts.add(tv.asString().getValue());
            }
        }
    }

    /**
     * Appends {@code embeddingField} (as a BSON array of doubles) to each target
     * document with its corresponding vector. Targets with a {@code null} vector
     * (a provider returning fewer vectors than requested) are left untouched.
     */
    static void applyEmbeddings(List<BsonDocument> targets, List<float[]> vectors, String embeddingField) {
        for (int i = 0; i < targets.size() && i < vectors.size(); i++) {
            var vector = vectors.get(i);
            if (vector == null) {
                continue;
            }
            var arr = new BsonArray();
            for (var f : vector) {
                arr.add(new BsonDouble(f));
            }
            targets.get(i).append(embeddingField, arr);
        }
    }

    /**
     * Resolved lazily (rather than in {@link #init()}) so that this interceptor
     * does not depend on plugin initialization order relative to the configured
     * provider — by the time requests are handled, every plugin's {@code @OnInit}
     * has already run. Cached per provider name (not a single field) because
     * {@link RequestOverrides#EMBEDDING_PROVIDER} can name a different provider on
     * a per-request basis — different tenants may use different vendors.
     */
    private EmbeddingModel resolveEmbeddingModel(String providerName) {
        var model = PluginModelResolver.resolve(registry, resolvedModels, providerName, EmbeddingModel.class);
        if (model.isEmpty()) {
            LOGGER.warn("autoEmbeddingInterceptor: embedding provider '{}' not found, not enabled, "
                + "or does not supply an EmbeddingModel", providerName);
        }
        return model.orElse(null);
    }
}
