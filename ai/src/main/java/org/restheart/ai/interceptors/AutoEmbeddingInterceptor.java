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

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonValue;
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

    private String providerName;
    private boolean enabled = false;

    // resolved lazily on first handle(), once all plugins have finished @OnInit
    private EmbeddingModel embeddingModel;

    @OnInit
    public void init() {
        this.providerName = argOrDefault(config, "embedding-provider", "");
        this.enabled = providerName != null && !providerName.isBlank();

        if (!enabled) {
            LOGGER.warn("autoEmbeddingInterceptor: no embedding-provider configured, interceptor is a no-op");
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
            && request.isHandledBy("mongo")
            && request.isWriteDocument()
            && !response.isInError()
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

        var model = resolveEmbeddingModel();
        if (model == null) {
            return;
        }

        List<float[]> vectors;
        try {
            vectors = model.embed(texts);
        } catch (Exception e) {
            LOGGER.error("autoEmbeddingInterceptor: failed to generate embeddings via '{}': {}",
                providerName, e.getMessage(), e);
            response.addWarning("auto-embedding failed: " + e.getMessage());
            return;
        }

        applyEmbeddings(targets, vectors, embeddingField);
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
     * has already run.
     */
    private EmbeddingModel resolveEmbeddingModel() {
        if (embeddingModel != null) {
            return embeddingModel;
        }

        var providerRecord = registry.getProviders().stream()
            .filter(p -> providerName.equals(p.getName()))
            .findFirst()
            .orElse(null);

        if (providerRecord == null || !providerRecord.isEnabled()) {
            LOGGER.warn("autoEmbeddingInterceptor: embedding provider '{}' not found or not enabled", providerName);
            return null;
        }

        var provided = providerRecord.getInstance().get(null);
        if (!(provided instanceof EmbeddingModel model)) {
            LOGGER.warn("autoEmbeddingInterceptor: provider '{}' does not supply an EmbeddingModel", providerName);
            return null;
        }

        this.embeddingModel = model;
        return model;
    }
}
