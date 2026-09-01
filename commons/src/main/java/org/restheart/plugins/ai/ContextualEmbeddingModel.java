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

/**
 * Optional capability an {@link EmbeddingModel} implementation may additionally
 * provide: embed every chunk of the same source document together, in a single
 * call, so each chunk's vector is computed with awareness of the other chunks
 * around it — typically producing more semantically coherent vectors for
 * retrieval than embedding each chunk in isolation via {@link EmbeddingModel#embed}.
 *
 * <p>Only meaningful for chunks that actually belong to one document. A caller
 * batching otherwise-unrelated texts in a single call (e.g. several independent
 * documents' fields) must keep using {@link EmbeddingModel#embed} — grouping
 * unrelated texts here would let each one's vector be influenced by the others'
 * content, which is not what "contextualized" means.
 *
 * <p>Support for this is provider/model-specific (e.g. Voyage AI's
 * {@code voyage-context-4} via its {@code /v1/contextualizedembeddings} endpoint,
 * {@code restheart-ai}'s {@code voyageContextualEmbeddingProvider}). A caller that
 * wants the benefit when available should resolve the configured
 * {@link EmbeddingModel}, check whether it also {@code instanceof
 * ContextualEmbeddingModel}, and fall back to {@link EmbeddingModel#embed} — called
 * once with all the chunks — when it does not.
 *
 * @see EmbeddingModel
 */
public interface ContextualEmbeddingModel {
    /**
     * @param chunksOfSameDocument every chunk of one source document, in order
     * @param request the request this call is made on behalf of — used to resolve
     *        per-request configuration overrides; may be {@code null} for callers
     *        outside a request context
     * @return one embedding vector per chunk, in the same order as {@code chunksOfSameDocument}
     */
    List<float[]> embedChunks(List<String> chunksOfSameDocument, Request<?> request);
}
