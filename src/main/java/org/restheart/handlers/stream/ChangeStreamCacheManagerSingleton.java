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
package org.restheart.handlers.stream;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import static org.restheart.db.DAOUtils.LOGGER;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */
public class ChangeStreamCacheManagerSingleton {

    private static final long CACHE_SIZE = 10000;
    private static final long CACHE_TTL = 1000;

    private final Cache<CacheableChangeStreamKey, CacheableChangeStreamCursor> CACHE = CacheFactory.createLocalCache(
            CACHE_SIZE,
            Cache.EXPIRE_POLICY.AFTER_WRITE,
            CACHE_TTL,
            (Map.Entry<CacheableChangeStreamKey, Optional<CacheableChangeStreamCursor>> entry) -> {
                if (entry.getValue().get().getSessions().size() > 0) {
                    ChangeStreamCacheManagerSingleton
                        .cacheChangeStreamCursor(entry.getKey(), entry.getValue().get());
                } else {
                    String message = "Removing change stream without clients listening for notifications; [url]: ";
                    message += entry.getKey().getUrl() + "; ";
                    message += "[stages]: ";
                    message += entry.getKey().getAVars().toString();
                    LOGGER.info(message);
                }
            });

    public static ChangeStreamCacheManagerSingleton getInstance() {

        return CacheManagerSingletonHolder.INSTANCE;

    }

    public static void cleanUp() {
        ChangeStreamCacheManagerSingleton
                .getInstance().CACHE.cleanUp();
    }

    public static void cacheChangeStreamCursor(CacheableChangeStreamKey key, CacheableChangeStreamCursor cacheableCursor) {
        ChangeStreamCacheManagerSingleton.getInstance().CACHE.put(key, cacheableCursor);
    }

    public static CacheableChangeStreamCursor getCachedChangeStreamIterable(CacheableChangeStreamKey key) {

        Optional<CacheableChangeStreamCursor> result
                = ChangeStreamCacheManagerSingleton
                        .getInstance().CACHE.get(key);

        if (result != null) {
            return result.get();
        }

        return null;
    }

    public static Collection<Optional<CacheableChangeStreamCursor>> getCachedChangeStreams() {

        Map<CacheableChangeStreamKey, Optional<CacheableChangeStreamCursor>> result
                = ChangeStreamCacheManagerSingleton
                        .getInstance().CACHE
                        .asMap();

        return result.values();
    }

    public static Map<CacheableChangeStreamKey, Optional<CacheableChangeStreamCursor>> getCacheAsMap() {

        return ChangeStreamCacheManagerSingleton
                .getInstance().CACHE
                .asMap();
    }

    public static Set<CacheableChangeStreamKey> getChangeStreamsKeySet() {
        return ChangeStreamCacheManagerSingleton.getInstance().CACHE
                .asMap()
                .keySet();
    }

    public static void removeChangeStream(CacheableChangeStreamKey key) {
        ChangeStreamCacheManagerSingleton.getInstance().CACHE
                .invalidate(key);
    }

    private static class CacheManagerSingletonHolder {

        private static final ChangeStreamCacheManagerSingleton INSTANCE = new ChangeStreamCacheManagerSingleton();
    }

    /**
     * @return the cache
     */
}
