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
import org.restheart.ai.util.CollectionEmbeddingConfig;
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
 * Automatically generates the embedding vectors of documents written to collections
 * that declare embedding rules in their {@code vectorSearch} metadata (see
 * {@link CollectionEmbeddingConfig}), using the {@code Provider<EmbeddingModel>} each rule
 * names, or the one in this interceptor's own {@code embedding-provider} configuration
 * (e.g. {@code openAIEmbeddingProvider}, {@code voyageEmbeddingProvider},
 * {@code ollamaEmbeddingProvider}).
 *
 * <p>This is the write-side counterpart to Phase 1's MongoDB {@code autoEmbed}
 * index type: where {@code autoEmbed} lets MongoDB itself generate embeddings,
 * this interceptor generates them in RESTHeart via a pluggable provider — for
 * deployments that don't have {@code autoEmbed} available or want a specific
 * embedding model/vendor.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * autoEmbeddingInterceptor:
 *   enabled: false                          # must be explicitly enabled
 *   embedding-provider: openAIEmbeddingProvider   # name of a configured Provider<EmbeddingModel>
 * }</pre>
 *
 * <h2>Enable on a collection</h2>
 * <pre>{@code
 * PATCH /mydb/articles
 * { "vectorSearch": { "textField": "description", "embeddingField": "embedding" } }
 *
 * PATCH /mydb/legal
 * { "vectorSearch": [
 *     { "textField": "summary", "embeddingField": "summaryVector", "model": "voyage-law-2" },
 *     { "textField": "body", "embeddingField": "bodyVector",
 *       "provider": "voyageContextualEmbeddingProvider", "model": "voyage-context-4" }
 * ] }
 * }</pre>
 *
 * <p>Documents written with {@code POST}/{@code PUT}/{@code PATCH} (single document
 * or a bulk array) get, for every rule whose {@code textField} they carry as a string,
 * that rule's {@code embeddingField} array appended before the write reaches MongoDB.
 * A document without a rule's text field is left as it is for that rule: a {@code PATCH}
 * that carries only {@code summary} re-embeds {@code summaryVector} and leaves
 * {@code bodyVector} untouched. One provider call per rule per request, with every
 * document that has the rule's text field batched in it.
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
        if (!request.isHandledBy("mongo") || !request.isWriteDocument() || response.isInError()) {
            return false;
        }
        return !CollectionEmbeddingConfig.rules(request.getCollectionProps()).isEmpty();
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var docs = asDocumentList(request.getContent());
        if (docs.isEmpty()) {
            return;
        }

        for (var rule : CollectionEmbeddingConfig.rules(request.getCollectionProps())) {
            var targets = new ArrayList<BsonDocument>();
            var texts = new ArrayList<String>();
            collectEmbeddableTexts(docs, rule.textField(), targets, texts);
            if (texts.isEmpty()) {
                continue;
            }

            // the rule may name its own provider, model and vector length: attached as the request
            // overrides the providers read, for this rule only, and put back for the next
            var previous = CollectionEmbeddingConfig.attach(request, rule, defaultProviderName);
            try {
                embed(request, response, rule, targets, texts);
            } finally {
                CollectionEmbeddingConfig.restore(request, previous);
            }
        }
    }

    /** Embeds the texts of one rule and writes the vectors to its field; a failure is a warning, not a refused write. */
    private void embed(MongoRequest request, MongoResponse response, CollectionEmbeddingConfig.EmbeddingRule rule,
            List<BsonDocument> targets, List<String> texts) {
        var providerName = effectiveProviderName(request);
        if (providerName.isBlank()) {
            LOGGER.debug("autoEmbeddingInterceptor: no embedding provider for '{}', neither on the rule nor configured nor overridden", rule.embeddingField());
            return;
        }

        var model = resolveEmbeddingModel(providerName);
        if (model == null) {
            // the write goes through, as it does when the provider fails: without the vector, and
            // the response says why, since a collection that looks configured and embeds nothing
            // would otherwise surface only as a search that finds nothing
            response.addWarning("auto-embedding of '" + rule.embeddingField() + "' skipped: embedding provider '"
                    + providerName + "' not found, not enabled, or does not supply an EmbeddingModel");
            return;
        }

        List<float[]> vectors;
        try {
            vectors = model.embed(texts, request);
        } catch (Exception e) {
            LOGGER.error("autoEmbeddingInterceptor: failed to generate embeddings for '{}' via '{}': {}",
                    rule.embeddingField(), providerName, e.getMessage(), e);
            response.addWarning("auto-embedding of '" + rule.embeddingField() + "' failed: " + e.getMessage());
            return;
        }

        applyEmbeddings(targets, vectors, rule.embeddingField());
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
        for (int i = 0;i < targets.size() && i < vectors.size();i++) {
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
