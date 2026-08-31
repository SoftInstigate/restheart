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

import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

/**
 * Generates embedding vectors for text. Supplied to other plugins via a
 * {@link Provider}&lt;{@code EmbeddingModel}&gt;, e.g. {@code openAIEmbeddingProvider}
 * ({@code restheart-ai}).
 *
 * <pre>{@code
 * @RegisterPlugin(name = "myEmbeddingProvider", description = "...")
 * public class MyEmbeddingProvider implements Provider<EmbeddingModel> {
 *     @Override
 *     public EmbeddingModel get(PluginRecord<?> caller) {
 *         return texts -> myClient.embed(texts);
 *     }
 * }
 * }</pre>
 *
 * <p>Consumers inject the model, not the provider, by the provider's registered name:
 *
 * <pre>{@code
 * @RegisterPlugin(name = "autoEmbeddingInterceptor", description = "...")
 * public class AutoEmbeddingInterceptor implements MongoInterceptor {
 *     @Inject("openAIEmbeddingProvider")
 *     private EmbeddingModel embeddingModel;
 * }
 * }</pre>
 *
 * @see Provider
 * @see RegisterPlugin
 */
public interface EmbeddingModel {
    /**
     * @param texts the texts to embed, in order
     * @return one embedding vector per input text, in the same order
     */
    List<float[]> embed(List<String> texts);
}
