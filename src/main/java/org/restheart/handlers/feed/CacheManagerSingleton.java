/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.feed;
import java.util.Optional;
import java.util.Set;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */
public class CacheManagerSingleton {

    private Cache<String, CacheableFeed> CACHE = CacheFactory.createLocalCache(
            1000,
            Cache.EXPIRE_POLICY.NEVER,
            0);

    public static CacheManagerSingleton getInstance() {

        return CacheManagerSingletonHolder.INSTANCE;

    }

    public static void cacheWebSocket(String uriPath, CacheableFeed feed) {
        CacheManagerSingleton.getInstance().CACHE
                .put(uriPath, feed);
    }

    public static CacheableFeed retrieveWebSocket(String uriPath) {

        Optional<CacheableFeed> result
                = CacheManagerSingleton
                        .getInstance().CACHE
                        .get(uriPath);
        
        if (result != null) {
            return result.get();
        }

        return null;
    }

    public static Set<String> retrieveResourcesUriSet() {
        return CacheManagerSingleton.getInstance().CACHE
                .asMap()
                .keySet();
    }

    public static void closeWebSocket(String uriPath) {
        CacheManagerSingleton.getInstance().CACHE
                .invalidate(uriPath);
    }

    private static class CacheManagerSingletonHolder {

        private static final CacheManagerSingleton INSTANCE = new CacheManagerSingleton();
    }

    /**
     * @return the cache
     */
}
