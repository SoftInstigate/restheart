/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.restheart.cache.LoadingCache;
import org.restheart.configuration.Configuration;
import org.restheart.graphql.GraphQLAppDefNotFoundException;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.cache.AppDefinitionLoader;
import org.restheart.graphql.cache.AppDefinitionRef;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
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
    private boolean enabled = true;

    @Inject("rh-config")
    private Configuration config;

    @Inject("gql-app-definition-cache")
    LoadingCache<AppDefinitionRef, GraphQLApp> gqlAppDefCache;

    @Inject("registry")
    private PluginsRegistry registry;

    @OnInit
    public void onInit() {
        Map<String, Object> graphqlArgs = config.getOrDefault("graphql", null);

        if (graphqlArgs != null) {
            this.enabled = isGQLSrvEnabled() && argOrDefault(graphqlArgs, "app-cache-enabled", true);
            this.TTR = argOrDefault(graphqlArgs, "app-cache-ttr", 60_000);
        } else {
            this.TTR = DEFAULT_TTR;
        }
    }

    private boolean isGQLSrvEnabled() {
        var gql$ = registry.getServices().stream().filter(s -> s.getName().equals("graphql")).findFirst();
        return gql$.isPresent() && gql$.get().isEnabled();
    }

    @Override
    public void init() {
        if (this.enabled) {
            Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(() -> ThreadsUtils.virtualThreadsExecutor()
                    .execute(this::revalidateCacheEntries), TTR, TTR, TimeUnit.MILLISECONDS);
        }
    }

    private void revalidateCacheEntries() {
        // work on a copy of the synchronized cache map, to avoid blocking it
        final var _cacheMap = new HashMap<>(this.gqlAppDefCache.asMap());

        _cacheMap.entrySet().stream()
            .filter(entry -> entry.getValue().isPresent())
            // filter out not updated documents (same etag than cached entry)
            .filter(entry -> AppDefinitionLoader.isUpdated(entry.getKey(), entry.getValue().get().getEtag()))
            .map(Map.Entry::getKey)
            .forEach(appUri -> {
                try {
                    var appDef = AppDefinitionLoader.load(appUri);
                    this.gqlAppDefCache.put(appUri, appDef);
                    LOGGER.debug("gql cache entry {} updated", appUri);
                } catch (GraphQLAppDefNotFoundException e) {
                    this.gqlAppDefCache.invalidate(appUri);
                    LOGGER.debug("gql cache entry {} removed", appUri);
                } catch (GraphQLIllegalAppDefinitionException e) {
                    this.gqlAppDefCache.invalidate(appUri);
                    LOGGER.warn("gql cache entry {} removed due to illegal definition", appUri, e);
                } catch (Throwable e) {
                    this.gqlAppDefCache.invalidate(appUri);
                    LOGGER.warn("error updating gql cache entry {}", appUri, e);
                }
        });
    }
}