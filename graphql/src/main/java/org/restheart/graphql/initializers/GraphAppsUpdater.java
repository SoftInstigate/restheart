/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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
package org.restheart.graphql.initializers;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.restheart.cache.LoadingCache;
import org.restheart.configuration.Configuration;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name="graphAppsUpdater",
        description = "periodically revalidates entries in GQL Apps cache",
        enabledByDefault = true
)
public class GraphAppsUpdater implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphAppsUpdater.class);
    private static final long DEFAULT_TTR = 60_000; // in milliseconds
    private long TTR = DEFAULT_TTR;

    @Inject("rh-config")
    private Configuration config;

    @Inject("gql-app-definition-cache")
    LoadingCache<String, GraphQLApp> gqlAppDefCache;

    @OnInit
    public void onInit() {
        Map<String, Object> graphqlArgs = config.getOrDefault("graphql", null);

        if (graphqlArgs != null) {
            this.TTR = argOrDefault(graphqlArgs, "app-cache-ttr", 60_000);
        } else {
            this.TTR = DEFAULT_TTR;
        }
    }

    @Override
    public void init() {
        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> ThreadsUtils.virtualThreadsExecutor()
                .execute(() -> this.revalidateCacheEntries()), TTR, TTR, TimeUnit.MILLISECONDS);
    }

    private void revalidateCacheEntries() {
        this.gqlAppDefCache.asMap().entrySet().stream()
            .filter(entry -> entry.getValue().isPresent())
            .map(entry -> entry.getKey())
            .forEach(appUri -> {
                try {
                    this.gqlAppDefCache.invalidate(appUri);
                    var app$ = this.gqlAppDefCache.getLoading(appUri);
                    if (app$.isPresent()) {
                        LOGGER.debug("gql cache entry {} updated", appUri);
                    } else {
                        this.gqlAppDefCache.invalidate(appUri);
                        LOGGER.debug("gql cache entry {} removed", appUri);
                    }

                } catch (Exception e) {
                    LOGGER.warn("error updaring gql cache entry {}", appUri, e);
                }
        });
    }
}