/*-
 * ========================LICENSE_START=================================
 * restheart-stripe
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
package org.restheart.stripe;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.stripe.util.StripeCatalogCache;

/**
 * Provides the single {@link StripeCatalogCache} instance shared by
 * {@code stripePlansService} (reads it) and {@code stripeWebhookService} (invalidates it on
 * {@code product.updated} / {@code price.updated}).
 *
 * <p>A plain cache object cannot be {@code @Inject}ed directly — only {@link Provider}
 * implementations are valid injection targets in RESTHeart — hence this thin wrapper.
 */
@RegisterPlugin(
        name = "stripeCatalogCache",
        description = "Provides the shared StripeCatalogCache for the plan catalog display data",
        enabledByDefault = false)
public class StripeCatalogCacheProvider implements Provider<StripeCatalogCache> {

    private final StripeCatalogCache cache = new StripeCatalogCache();

    @Override
    public StripeCatalogCache get(PluginRecord<?> caller) {
        return cache;
    }
}
