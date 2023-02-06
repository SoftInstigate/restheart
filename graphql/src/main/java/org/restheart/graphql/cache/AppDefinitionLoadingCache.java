/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2023 SoftInstigate
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
import org.restheart.utils.LambdaUtils;

public class AppDefinitionLoadingCache {

    private static AppDefinitionLoadingCache instance = null;
    private static long TTL = 100_000;
    private static final long MAX_CACHE_SIZE = 1_000;

    private LoadingCache<String, GraphQLApp> appLoadingCache;

    private AppDefinitionLoadingCache(){
        this.appLoadingCache = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE,
            Cache.EXPIRE_POLICY.AFTER_WRITE, TTL, key -> {
                try {
                    return AppDefinitionLoader.loadAppDefinition(key);
                } catch (GraphQLIllegalAppDefinitionException e) {
                    LambdaUtils.throwsSneakyException(e);
                    return null;
                }
            });
    }

    public static void setTTL(long ttl) {
        TTL = ttl;
    }

    public static AppDefinitionLoadingCache getInstance(){
        if (instance == null){
            instance = new AppDefinitionLoadingCache();
        }

        return instance;
    }

    public GraphQLApp get(String appName) throws GraphQLAppDefNotFoundException, GraphQLIllegalAppDefinitionException {
        var _app = this.appLoadingCache.get(appName);

        if (_app != null && _app.isPresent()){
            return _app.get();
        } else {
            try {
                _app = this.appLoadingCache.getLoading(appName);
            } catch (Exception e){
                throw new GraphQLIllegalAppDefinitionException(e.getMessage());
            }

            if (_app != null && _app.isPresent()) {
                return _app.get();
            } else {
                throw new GraphQLAppDefNotFoundException("Valid configuration for " + appName + " not found. ");
            }
        }
    }
}