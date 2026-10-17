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

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.ai.util.CollectionEmbeddingConfig;
import org.restheart.ai.util.RequestOverrides;
import org.restheart.ai.util.RuleEmbedder;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
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

    // the one place that embeds by a collection's rules; see RuleEmbedder
    private RuleEmbedder embedder;

    @OnInit
    public void init() {
        this.defaultProviderName = argOrDefault(config, "embedding-provider", "");
        this.enabled = defaultProviderName != null && !defaultProviderName.isBlank();

        if (!enabled) {
            LOGGER.info("autoEmbeddingInterceptor: no default embedding-provider: a rule embeds only when it names its "
                    + "provider, or a request overrides it via {}", RequestOverrides.EMBEDDING_PROVIDER);
        }
        this.embedder = new RuleEmbedder(registry, defaultProviderName);
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
        // a failure is a warning, not a refused write: the documents go without that vector
        embedder.embed(request, CollectionEmbeddingConfig.rules(request.getCollectionProps()), docs)
                .forEach(response::addWarning);
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

}
