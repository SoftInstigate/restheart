/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.db;

import com.mongodb.DBCursor;
import org.restheart.Bootstrapper;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DBCursorPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBCursorPool.class);
    
    private final DbsDAO dbsDAO;

    //TODO make those configurable
    private final int SKIP_SLICE_LINEAR_DELTA = Bootstrapper.getConfiguration().getEagerLinearSliceDelta();
    private final int SKIP_SLICE_LINEAR_WIDTH = Bootstrapper.getConfiguration().getEagerLinearSliceWidht();
    private final int[] SKIP_SLICES_HEIGHTS = Bootstrapper.getConfiguration().getEagerLinearSliceHeights();

    private final int SKIP_SLICE_RND_MIN_WIDTH = Bootstrapper.getConfiguration().getEagerRndSliceMinWidht();
    private final int SKIP_SLICE_RND_MAX_CURSORS = Bootstrapper.getConfiguration().getEagerRndMaxCursors();

    public enum EAGER_CURSOR_ALLOCATION_POLICY {

        LINEAR,
        RANDOM,
        NONE
    };

    private final Cache<DBCursorPoolEntryKey, DBCursor> cache;
    private final LoadingCache<DBCursorPoolEntryKey, Long> collSizes;

    private static final long TTL = 8*60*1000; // in minutes - MUST BE < 10 since this 10 the TTL of the cursor in mongodb
    private static final long POOL_SIZE = Bootstrapper.getConfiguration().getEagerPoolSize();

    ExecutorService executor = Executors.newSingleThreadExecutor();

    public static DBCursorPool getInstance() {
        return DBCursorPoolSingletonHolder.INSTANCE;
    }

    private DBCursorPool(DbsDAO dbsDAO) {
        this.dbsDAO = dbsDAO;
        
        cache = CacheFactory.createLocalCache(POOL_SIZE, Cache.EXPIRE_POLICY.AFTER_READ, TTL, (Map.Entry<DBCursorPoolEntryKey, Optional<DBCursor>> entry) -> {
            if (entry != null && entry.getValue() != null) {
                entry.getValue().ifPresent(v -> v.close());
            }
        });

        collSizes = CacheFactory.createLocalLoadingCache(100, org.restheart.cache.Cache.EXPIRE_POLICY.AFTER_WRITE, 60*1000, (DBCursorPoolEntryKey key) -> {
            return dbsDAO.getCollectionSize(key.getCollection(), key.getFilter());
        }
        );

        if (LOGGER.isDebugEnabled()) {
            // print stats every 1 minute
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                getCacheSizes().forEach((s, c) -> {
                    LOGGER.debug("db cursor pool size: {}\t{}", s, c);
                });

                LOGGER.trace("db cursor pool entries: {}", cache.asMap().keySet());
            }, 1, 1, TimeUnit.MINUTES);
        }
    }

    public synchronized SkippedDBCursor get(DBCursorPoolEntryKey key, EAGER_CURSOR_ALLOCATION_POLICY allocationPolicy) {
        if (key.getSkipped() < SKIP_SLICE_LINEAR_WIDTH) {
            LOGGER.trace("no cursor to reuse found with skipped {} that is less than SKIP_SLICE_WIDTH {}", key.getSkipped(), SKIP_SLICE_LINEAR_WIDTH);
            return null;
        }

        // return the dbcursor with the closest skips to the request
        Optional<DBCursorPoolEntryKey> _bestKey = cache.asMap().keySet().stream()
                .filter(cursorsPoolFilterGt(key))
                .sorted(Comparator.comparingInt(DBCursorPoolEntryKey::getSkipped).reversed())
                .findFirst();

        SkippedDBCursor ret;

        if (_bestKey.isPresent()) {
            Optional<DBCursor> _dbcur = cache.get(_bestKey.get());

            if (_dbcur != null && _dbcur.isPresent()) {
                ret = new SkippedDBCursor(_dbcur.get(), _bestKey.get().getSkipped());
                cache.invalidate(_bestKey.get());

                LOGGER.debug("found cursor to reuse in pool, asked with skipped {} and saving {} seeks", key.getSkipped(), _bestKey.get().getSkipped());
            } else {
                ret = null;

                LOGGER.debug("no cursor to reuse found with skipped {}.", key.getSkipped());
            }
        } else {
            ret = null;

            LOGGER.debug("no cursor to reuse found with skipped {}.", key.getSkipped());
        }

        populateCache(key, allocationPolicy);

        return ret;
    }

    private void populateCache(DBCursorPoolEntryKey key, EAGER_CURSOR_ALLOCATION_POLICY allocationPolicy) {
        if (allocationPolicy == EAGER_CURSOR_ALLOCATION_POLICY.LINEAR) {
            populateCacheLinear(key);
        } else if (allocationPolicy == EAGER_CURSOR_ALLOCATION_POLICY.RANDOM) {
            populateCacheRandom(key);
        }
    }

    private void populateCacheLinear(DBCursorPoolEntryKey key) {
        if (key.getSkipped() < SKIP_SLICE_LINEAR_WIDTH) {
            return;
        }

        int firstSlice = key.getSkipped() / SKIP_SLICE_LINEAR_WIDTH;

        executor.submit(() -> {
            int slice = firstSlice;

            for (int tohave : SKIP_SLICES_HEIGHTS) {
                int sliceSkips = slice * SKIP_SLICE_LINEAR_WIDTH - SKIP_SLICE_LINEAR_DELTA;
                DBCursorPoolEntryKey sliceKey = new DBCursorPoolEntryKey(key.getCollection(), key.getSort(), key.getFilter(), sliceSkips, -1);

                long existing = getSliceHeight(sliceKey);

                for (long cont = tohave - existing; cont > 0; cont--) {
                    DBCursor cursor = dbsDAO.getCollectionDBCursor(key.getCollection(), key.getSort(), key.getFilter());
                    cursor.skip(sliceSkips);
                    DBCursorPoolEntryKey newkey = new DBCursorPoolEntryKey(key.getCollection(), key.getSort(), key.getFilter(), sliceSkips, System.nanoTime());
                    cache.put(newkey, cursor);
                    LOGGER.debug("created new cursor in pool: {}", newkey);
                }

                slice++;
            }
        });
    }

    private void populateCacheRandom(DBCursorPoolEntryKey key) {
        executor.submit(() -> {
            Long size = collSizes.getLoading(key).get();

            int sliceWidht;
            int slices = 0;
            int totalSlices = size.intValue() / SKIP_SLICE_RND_MIN_WIDTH + 1;

            if (totalSlices <= SKIP_SLICE_RND_MAX_CURSORS) {
                slices = totalSlices;
                sliceWidht = SKIP_SLICE_RND_MIN_WIDTH;
            } else {
                slices = SKIP_SLICE_RND_MAX_CURSORS;
                sliceWidht = size.intValue() / slices;
            }

            for (int slice = 1; slice < slices; slice++) {
                int sliceSkips = (int) slice * sliceWidht;

                DBCursorPoolEntryKey sliceKey = new DBCursorPoolEntryKey(key.getCollection(), key.getSort(), key.getFilter(), sliceSkips, -1);

                long existing = getSliceHeight(sliceKey);

                for (long cont = 1 - existing; cont > 0; cont--) {
                    DBCursor cursor = dbsDAO.getCollectionDBCursor(key.getCollection(), key.getSort(), key.getFilter());
                    cursor.skip(sliceSkips);
                    DBCursorPoolEntryKey newkey = new DBCursorPoolEntryKey(key.getCollection(), key.getSort(), key.getFilter(), sliceSkips, System.nanoTime());
                    cache.put(newkey, cursor);
                    LOGGER.debug("created new cursor in pool: {}", newkey);
                }
            }
        });
    }

    private long getSliceHeight(DBCursorPoolEntryKey key) {
        long ret = cache.asMap().keySet().stream()
                .filter(cursorsPoolFilterEq(key))
                .count();

        LOGGER.trace("cursor in pool with skips {} are {}", key.getSkipped(), ret);

        return ret;
    }

    private Predicate<? super DBCursorPoolEntryKey> cursorsPoolFilterEq(DBCursorPoolEntryKey key) {
        return k
                -> Objects.equals(k.getCollection().getDB().getName(), key.getCollection().getDB().getName())
                && Objects.equals(k.getCollection().getName(), key.getCollection().getName())
                && Arrays.equals(k.getFilter() != null ? k.getFilter().toArray() : null, key.getFilter() != null ? key.getFilter().toArray() : null)
                && Arrays.equals(k.getSort() != null ? k.getSort().toArray() : null, key.getSort() != null ? key.getSort().toArray() : null)
                && k.getSkipped() == key.getSkipped();
    }
    
    private Predicate<? super DBCursorPoolEntryKey> cursorsPoolFilterGt(DBCursorPoolEntryKey key) {
        return k
                -> Objects.equals(k.getCollection().getDB().getName(), key.getCollection().getDB().getName())
                && Objects.equals(k.getCollection().getName(), key.getCollection().getName())
                && Arrays.equals(k.getFilter() != null ? k.getFilter().toArray() : null, key.getFilter() != null ? key.getFilter().toArray() : null)
                && Arrays.equals(k.getSort() != null ? k.getSort().toArray() : null, key.getSort() != null ? key.getSort().toArray() : null)
                && k.getSkipped() < key.getSkipped();
    }

    private TreeMap<String, Long> getCacheSizes() {
        return new TreeMap<>(cache.asMap().keySet().stream().collect(Collectors.groupingBy(DBCursorPoolEntryKey::getCacheStatsGroup, Collectors.counting())));
    }

    private static class DBCursorPoolSingletonHolder {

        private static final DBCursorPool INSTANCE = new DBCursorPool(new DbsDAO());

    };
}
