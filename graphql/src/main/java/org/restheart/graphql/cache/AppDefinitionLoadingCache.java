/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2024 SoftInstigate
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

import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.graphql.GraphQLAppDefNotFoundException;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.LambdaUtils;

@RegisterPlugin(name="gql-app-definition-cache", description="provides access to the GQL App Definition cache")
public class AppDefinitionLoadingCache implements Provider<LoadingCache> {
    private static final long MAX_CACHE_SIZE = 100_000;

    private static final LoadingCache<String, GraphQLApp> CACHE = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE, Cache.EXPIRE_POLICY.NEVER, 0,
        appURI -> {
            try {
                return AppDefinitionLoader.loadAppDefinition(appURI);
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

    public static GraphQLApp get(String appURI) throws GraphQLAppDefNotFoundException, GraphQLIllegalAppDefinitionException {
        var _app = CACHE.get(appURI);

        if (_app != null && _app.isPresent()){
            return _app.get();
        } else {
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
    }

    @Override
    public LoadingCache<String, GraphQLApp> get(PluginRecord<?> caller) {
        return CACHE;
    }
}