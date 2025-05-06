/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2025 SoftInstigate
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
package org.restheart.graphql.cache;

import java.util.Map;

import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.configuration.Configuration;
import org.restheart.graphql.GraphQLAppDefNotFoundException;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.LambdaUtils;

@RegisterPlugin(name="gql-app-definition-cache", description="provides access to the GQL App Definition cache")
public class AppDefinitionLoadingCache implements Provider<LoadingCache> {
    private static final long MAX_CACHE_SIZE = 100_000;
    private static boolean enabled = true;

    @Inject("rh-config")
    private Configuration config;

    @Inject("registry")
    private PluginsRegistry registry;

    @OnInit
    public void onInit() {
        Map<String, Object> graphqlArgs = config.getOrDefault("graphql", null);
        if (graphqlArgs != null) {
            enabled = isGQLSrvEnabled() && argOrDefault(graphqlArgs, "app-cache-enabled", true);;
        } else {
            enabled = false;
        }
    }

    private boolean isGQLSrvEnabled() {
        var gql$ = registry.getServices().stream().filter(s -> s.getName().equals("graphql")).findFirst();
        return gql$.isPresent() && gql$.get().isEnabled();
    }

    private static final LoadingCache<String, GraphQLApp> CACHE = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE, Cache.EXPIRE_POLICY.NEVER, 0,
        appURI -> {
            try {
                return AppDefinitionLoader.load(appURI);
            } catch (GraphQLAppDefNotFoundException e) {
                return null;
            } catch (GraphQLIllegalAppDefinitionException e) {
                LambdaUtils.throwsSneakyException(e);
                return null;
            }
        });

    public static LoadingCache<String, GraphQLApp> getCache() {
        return CACHE;
    }

    public static GraphQLApp getLoading(String appURI) throws GraphQLAppDefNotFoundException, GraphQLIllegalAppDefinitionException {
        if (enabled) {
            var _app = CACHE.get(appURI);

            if (_app != null && _app.isPresent()){
                return _app.get();
            } else {
                // Remove key if null was cached (previous request missed it, but it may exist now)
                if (_app != null && _app.isEmpty()) {
                    CACHE.remove(appURI);
                }

                try {
                    _app = CACHE.getLoading(appURI);
                } catch (Exception e) {
                    throw new GraphQLIllegalAppDefinitionException(e.getMessage(), e);
                }

                if (_app != null && _app.isPresent()) {
                    return _app.get();
                } else {
                    throw new GraphQLAppDefNotFoundException("GQL App Definition for uri " + appURI + " not found. ");
                }
            }
        } else {
            return AppDefinitionLoader.load(appURI);
        }
    }

    /**
     * Implementation of the get() method of the Provider interface
     */
    @Override
    public LoadingCache<String, GraphQLApp> get(PluginRecord<?> caller) {
        return CACHE;
    }
}