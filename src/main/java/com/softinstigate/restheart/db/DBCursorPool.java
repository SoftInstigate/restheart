/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package com.softinstigate.restheart.db;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mongodb.DBCursor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare
 */
public class DBCursorPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBCursorPool.class);

    //TODO make those configurable
    private final int SKIP_SLICE_DELTA = 100;
    private final int SKIP_SLICE_WIDTH = 1000;

    private final int[] LINEAR_SLICES_HEIGHT = new int[]{5, 2, 1};
    private final int RND_SLICE_HEIGHT = 2;

    public enum EAGER_ALLOCATION_POLICY {
        LINEAR, RANDOM, NONE
    };

    private final Cache<DBCursorPoolEntryKey, DBCursor> cache;

    private static final long TTL = 5; // in minutes - MUST BE < 10 since this 10 the TTL of the cursor in mongodb
    private static final long POOL_SIZE = 100;

    ExecutorService executor = Executors.newSingleThreadExecutor();

    private DBCursorPool() {
        CacheBuilder builder = CacheBuilder.newBuilder()
                .maximumSize(POOL_SIZE)
                .expireAfterAccess(TTL, TimeUnit.MINUTES)
                .recordStats();

        cache = builder.build();

        if (LOGGER.isInfoEnabled()) {
            // print stats every 1 minute
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                LOGGER.info("db cursor pool stats: {}", stats());
                LOGGER.info("db cursor pool size: {} ", cache.size());
                LOGGER.debug("db cursor pool entries: {}", cache.asMap().keySet());
            }, 1, 1, TimeUnit.MINUTES);
        }
    }

    public synchronized SkippedDBCursor get(DBCursorPoolEntryKey key) {
        return get(key, EAGER_ALLOCATION_POLICY.LINEAR);
    }

    public synchronized SkippedDBCursor get(DBCursorPoolEntryKey key, EAGER_ALLOCATION_POLICY allocationPolicy) {
        if (key.getSkipped() < SKIP_SLICE_WIDTH) {
            LOGGER.debug("no cursor to reuse found with skipped {} that is less than SKIP_SLICE_WIDTH {}", key.getSkipped(), SKIP_SLICE_WIDTH);
            return null;
        }

        // return the dbursor closer to the request
        Optional<DBCursorPoolEntryKey> _bestKey = cache.asMap().keySet().stream().filter(k
                -> Objects.equals(k.getCollection().getDB().getName(), key.getCollection().getDB().getName())
                && Objects.equals(k.getCollection().getName(), key.getCollection().getName())
                && Arrays.equals(k.getFilter() != null ? k.getFilter().toArray() : null, key.getFilter() != null ? key.getFilter().toArray() : null)
                && Arrays.equals(k.getSort() != null ? k.getSort().toArray() : null, key.getSort() != null ? key.getSort().toArray() : null)
                && k.getSkipped() <= key.getSkipped()
        ).sorted(Comparator.comparingInt(DBCursorPoolEntryKey::getSkipped).reversed()).findFirst();

        SkippedDBCursor ret;

        if (_bestKey.isPresent()) {
            ret = new SkippedDBCursor(cache.getIfPresent(_bestKey.get()), _bestKey.get().getSkipped());
            cache.invalidate(_bestKey.get());

            LOGGER.debug("found cursor to reuse in pool, asked with skipped {} and saving {} seeks", key.getSkipped(), _bestKey.get().getSkipped());
        } else {
            LOGGER.debug("no cursor to reuse found with skipped {}.", key.getSkipped());
            cache.getIfPresent(new DBCursorPoolEntryKey(null, null, null, -1, -1)); // just to update the cache missed stats
            ret = null;
        }

        populateCache(key, allocationPolicy);

        return ret;
    }

    private void populateCache(DBCursorPoolEntryKey key, EAGER_ALLOCATION_POLICY allocationPolicy) {
        if (allocationPolicy == EAGER_ALLOCATION_POLICY.LINEAR) {
            populateCacheLinear(key);
        } else if (allocationPolicy == EAGER_ALLOCATION_POLICY.RANDOM) {
            populateCacheRandom(key);
        }
    }

    private void populateCacheLinear(DBCursorPoolEntryKey key) {
        if (key.getSkipped() < SKIP_SLICE_WIDTH)
            return;
        
        int firstSlice = (key.getSkipped() / SKIP_SLICE_WIDTH);
        
        executor.submit(() -> {
            int slice = firstSlice;

            for (int tohave : LINEAR_SLICES_HEIGHT) {
                int sliceSkips = slice * SKIP_SLICE_WIDTH - SKIP_SLICE_DELTA;
                DBCursorPoolEntryKey sliceKey = new DBCursorPoolEntryKey(key.getCollection(), key.getSort(), key.getFilter(), sliceSkips, -1);
                
                long existing = getSliceHeight(sliceKey);
                
                for (long cont = tohave - existing; cont > 0; cont --) {
                    DBCursor cursor = CollectionDAO.getCollectionDBCursor(key.getCollection(), key.getSort(), key.getFilter());
                    cursor.skip(sliceSkips);
                    DBCursorPoolEntryKey newkey = new DBCursorPoolEntryKey(key.getCollection(), key.getSort(), key.getFilter(), sliceSkips, System.currentTimeMillis());
                    cache.put(newkey, cursor);
                    LOGGER.debug("created new cursor in pool: {}", newkey);
                }
                
                slice++;
            }
        });
    }

    private void populateCacheRandom(DBCursorPoolEntryKey key) {
        throw new RuntimeException("not yet implemented");
    }
    
    private long getSliceHeight(DBCursorPoolEntryKey key) {
        long ret = cache.asMap().keySet().stream().filter(k
                -> Objects.equals(k.getCollection().getDB().getName(), key.getCollection().getDB().getName())
                && Objects.equals(k.getCollection().getName(), key.getCollection().getName())
                && Arrays.equals(k.getFilter() != null ? k.getFilter().toArray() : null, key.getFilter() != null ? key.getFilter().toArray() : null)
                && Arrays.equals(k.getSort() != null ? k.getSort().toArray() : null, key.getSort() != null ? key.getSort().toArray() : null)
                && k.getSkipped() == key.getSkipped()
        ).count();
        
        LOGGER.debug("cursor in pool with skips {} are {}", key.getSkipped(), ret);
        
        return ret;
    }

    public final String stats() {
        return cache.stats().toString();
    }

    /**
     *
     * @return
     */
    public static DBCursorPool getInstance() {
        return DBCursorPoolSingletonHolder.INSTANCE;

    }

    private static class DBCursorPoolSingletonHolder {
        private static final DBCursorPool INSTANCE = new DBCursorPool();
    }
}
