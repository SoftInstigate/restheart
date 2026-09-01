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

import java.util.Map;

import org.restheart.mongodb.utils.CustomOperatorRegistry;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the {@code $vectorize} custom aggregation operator (see
 * {@link VectorizeOperator}) at boot, via the {@code CustomOperatorRegistry} SPI
 * (the aggregation-pipeline counterpart to {@code restheart-stripe}'s use of
 * {@code AclVarsRegistry} to register {@code @subscription}).
 *
 * <h2>Configuration (plugins-args)</h2>
 * <pre>{@code
 * plugins-args:
 *   vectorizeOperator:
 *     enabled: false                          # must be explicitly enabled
 *     embedding-provider: openAIEmbeddingProvider   # name of a configured Provider<EmbeddingModel>
 * }</pre>
 *
 * <p>{@code embedding-provider} is this plugin's own static default — per request, a
 * deployment's tenant-config interceptor may attach
 * {@code RequestOverrides.EMBEDDING_PROVIDER} to route {@code $vectorize} to a
 * different provider, the same override key {@code autoEmbeddingInterceptor} reads.
 */
@RegisterPlugin(
    name = "vectorizeOperator",
    description = "Registers the $vectorize custom aggregation operator (text to embedding vector, inline in pipelines)",
    initPoint = InitPoint.BEFORE_STARTUP,
    enabledByDefault = false
)
public class VectorizeOperatorInitializer implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(VectorizeOperatorInitializer.class);

    @Inject("config")
    private Map<String, Object> config;

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("custom-operator-registry")
    private CustomOperatorRegistry customOperatorRegistry;

    @Override
    public void init() {
        var defaultProviderName = argOrDefault(config, "embedding-provider", "");

        if (defaultProviderName.isBlank()) {
            LOGGER.warn("vectorizeOperator: no embedding-provider configured; $vectorize will only work "
                + "for requests that attach an override-ai-embedding-provider");
        }

        // let a registration failure (e.g. another plugin already registered $vectorize)
        // propagate and abort startup — same precedent as restheart-stripe's
        // StripeInitializer registering @subscription via AclVarsRegistry
        customOperatorRegistry.register(new VectorizeOperator(registry, defaultProviderName));
    }
}
