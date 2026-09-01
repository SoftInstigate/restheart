/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins.ai;

import java.util.List;

import org.restheart.exchange.Request;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

/**
 * Re-scores documents against a query. Supplied to other plugins via a
 * {@link Provider}&lt;{@code RerankModel}&gt;, e.g. {@code cohereRerankProvider} or
 * {@code voyageRerankProvider} ({@code restheart-ai}).
 *
 * <pre>{@code
 * @RegisterPlugin(name = "myRerankProvider", description = "...")
 * public class MyRerankProvider implements Provider<RerankModel> {
 *     @Override
 *     public RerankModel get(PluginRecord<?> caller) {
 *         return (query, documents, topK, request) -> myClient.rerank(query, documents, topK);
 *     }
 * }
 * }</pre>
 *
 * <p>Like {@link EmbeddingModel}, the {@link Request} is passed through so a
 * multi-tenant implementation can resolve per-request overrides rather than baking a
 * single tenant's configuration into a cached instance.
 *
 * @see EmbeddingModel
 * @see Provider
 * @see RegisterPlugin
 */
public interface RerankModel {
    /**
     * @param query the search query the documents are being ranked against
     * @param documents the documents to rank, in their original order — a
     *        {@link RankedResult#index()} refers back into this list
     * @param topK the maximum number of results to return; implementations may
     *        return fewer than {@code documents.size()} even without this limit
     * @param request the request this call is made on behalf of — used to resolve
     *        per-request configuration overrides; may be {@code null} for callers
     *        outside a request context
     * @return the ranked results, typically already sorted by descending
     *         {@link RankedResult#score()}
     */
    List<RankedResult> rerank(String query, List<String> documents, int topK, Request<?> request);
}
