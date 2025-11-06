/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
package org.restheart.mongodb.db;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.mongodb.client.ClientSession;
import org.bson.BsonDocument;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoCollection;


/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetCollectionCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCollectionCache.class);

    private static final boolean CACHE_ENABLED = MongoServiceConfiguration.get() == null || MongoServiceConfiguration.get().isGetCollectionCacheEnabled();
    private static final long CACHE_SIZE = MongoServiceConfiguration.get() == null ? 100 : MongoServiceConfiguration.get().getGetCollectionCacheSize();
    private static final long CACHE_TTL = MongoServiceConfiguration.get() == null ? 10_000 : MongoServiceConfiguration.get().getGetCollectionCacheTTL();

    /**
     *
     * @return
     */
    public static GetCollectionCache getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private final Cache<GetCollectionCacheKey, List<BsonDocument>> cache;

    private GetCollectionCache() {
        if (CACHE_ENABLED) {
            cache = CacheFactory.createLocalCache(CACHE_SIZE, Cache.EXPIRE_POLICY.AFTER_WRITE, CACHE_TTL);

            if (LOGGER.isTraceEnabled()) {
                // print stats every 1 minute
                Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                    getCacheSizes().forEach((s, c) -> {
                        LOGGER.debug("get collection cache size: {}\t{}", s, c);
                    });

                    LOGGER.trace("get collection cache entries: {}", cache.asMap().keySet());
                }, 1, 1, TimeUnit.MINUTES);
            }
        } else {
            cache = null;
        }
    }

    public void put(GetCollectionCacheKey key, List<BsonDocument> value) {
        if (cache == null) return;

        cache.put(key, value);
    }

    public Pair<GetCollectionCacheKey, List<BsonDocument>> find(GetCollectionCacheKey key) {
        if (cache == null) return null;

        return _get(key, false);
    }

    public List<BsonDocument> get(GetCollectionCacheKey key) {
        if (cache == null) return null;

        return _get(key, false).getValue();
    }

    public List<BsonDocument> remove(GetCollectionCacheKey key) {
        if (cache == null) return null;

        return _get(key, true).getValue();
    }

    /**
     *
     * @param key
     * @param remove
     * @return
     */
    private Pair<GetCollectionCacheKey, List<BsonDocument>> _get(GetCollectionCacheKey key, boolean remove) {
        if (cache == null) return null;

        // return the first entry with all available documents
		var _bestKey = cache.asMap().keySet().stream()
            .filter(cacheKeyFilter(key))
            .findFirst();

        if (_bestKey.isPresent()) {
            var _cached = remove ? cache.remove(_bestKey.get()) : cache.get(_bestKey.get());

            if (_cached != null && _cached.isPresent()) {
                LOGGER.debug("{} cached documents. cache entry id {}", ansi().fg(GREEN).bold().a("found").reset().toString(), _bestKey.get().cursorId());
                return new Pair<>(_bestKey.get(), _cached.get());
            } else {
                LOGGER.debug("{} cached documents.", ansi().fg(RED).bold().a("no").reset().toString());
                return null;
            }
        } else {
            LOGGER.debug(ansi().fg(RED).bold().a("missed").reset().toString() + " get collection cache.");
            return null;
        }
    }

    public void invalidate(GetCollectionCacheKey key) {
        if (cache == null) return;

        cache.invalidate(key);
    }

    public void invalidateAll(String db, String coll) {
        if (cache == null) return;

        cache.asMap().keySet().stream()
            .filter(k -> k.collection().getNamespace().getDatabaseName().equals(db))
            .filter(k -> k.collection().getNamespace().getCollectionName().equals(coll))
            .forEach(k -> cache.invalidate(k));
    }

    public void invalidateAll(String db) {
        if (cache == null) return;

        cache.asMap().keySet().stream()
            .filter(k -> k.collection().getNamespace().getDatabaseName().equals(db))
            .forEach(k -> cache.invalidate(k));
    }


    public void invalidateAll(MongoCollection<?> coll) {
        if (cache == null) return;

        cache.asMap().keySet().stream()
            .filter(k -> k.collection().getNamespace().equals(coll.getNamespace()))
            .forEach(cache::invalidate);
    }

    private Predicate<? super GetCollectionCacheKey> cacheKeyFilter(GetCollectionCacheKey requested) {
        if (cache == null) return null;

        return cached
            -> Objects.equals(cached.collection().getNamespace(), requested.collection().getNamespace())
            && Objects.equals(cached.filter(), requested.filter())
            && Objects.equals(cached.sort(), requested.sort())
            && Objects.equals(cached.keys(), requested.keys())
            && Objects.equals(cached.hints(), requested.hints())
            && ((cached.from() <= requested.from() && cached.to() >= requested.to())
                || (cached.exhausted() && cached.from() <= requested.from()))
            && sessionEquals(cached.session(), requested.session());
    }
    
    private boolean sessionEquals(Optional<ClientSession> s1, Optional<ClientSession> s2) {
        // Both empty
        if (s1.isEmpty() && s2.isEmpty()) {
            return true;
        }
        
        // One empty, one present
        if (s1.isEmpty() || s2.isEmpty()) {
            return false;
        }
        
        // Both present - compare by identity since ClientSession doesn't have meaningful equals
        return s1.get() == s2.get();
    }

    private TreeMap<String, Long> getCacheSizes() {
        if (cache == null) return null;

        return new TreeMap<>(cache.asMap()
            .keySet()
            .stream()
            .collect(Collectors.groupingBy(GetCollectionCacheKey::getCacheStatsGroup, Collectors.counting())));
    }

    private static class SingletonHolder {
        private static final GetCollectionCache INSTANCE = new GetCollectionCache();

        private SingletonHolder() {
        }
    }
}