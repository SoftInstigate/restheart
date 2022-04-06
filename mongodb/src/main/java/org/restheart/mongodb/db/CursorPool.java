/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mongodb.client.FindIterable;
import static java.lang.Thread.MIN_PRIORITY;
import java.util.Comparator;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.Color.YELLOW;
import static org.fusesource.jansi.Ansi.ansi;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.exchange.ExchangeKeys.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CursorPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(CursorPool.class);

    /**
     * Cursor in the pool won't be used if<br>REQUESTED_SKIPS - POOL_SKIPS &gt;
     * MIN_SKIP_DISTANCE_PERCENTAGE * REQUESTED_SKIPS.<br>The cursor from the
     * pool need to be iterated via the next() method (REQUESTED_SKIPS -
     * POOL_SKIPS) times to reach the requested page; since skip() is more
     * efficient than next(), using the cursor in the pool is worthwhile only if
     * next() has to be used less than MIN_SKIP_DISTANCE_PERCENTAGE *
     * REQUESTED_SKIPS times.
     */
    public static final double MIN_SKIP_DISTANCE_PERCENTAGE = 10 / 100f; // 10%
    // MUST BE < 10 since this 10 the TTL of the default cursor in mongodb
    private static final long TTL = 8 * 60 * 1000;
    private static final long POOL_SIZE = MongoServiceConfiguration.get().getEagerPoolSize();

    private static final ThreadPoolExecutor POOL_POPULATOR = new ThreadPoolExecutor(
        1, 2,
        1, TimeUnit.MINUTES,
        new ArrayBlockingQueue<>(1),
        new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("cursor-pool-populator-%d")
                .setPriority(MIN_PRIORITY)
                .build()
    );

    /**
     *
     * @return
     */
    public static CursorPool getInstance() {
        return DBCursorPoolSingletonHolder.INSTANCE;
    }

    private final Collections collections = Collections.get();

    private final int SKIP_SLICE_LINEAR_DELTA = MongoServiceConfiguration.get().getEagerLinearSliceDelta();

    private final int SKIP_SLICE_LINEAR_WIDTH = MongoServiceConfiguration.get().getEagerLinearSliceWidht();

    private final int[] SKIP_SLICES_HEIGHTS = MongoServiceConfiguration.get().getEagerLinearSliceHeights();

    private final int SKIP_SLICE_RND_MIN_WIDTH = MongoServiceConfiguration.get().getEagerRndSliceMinWidht();

    private final int SKIP_SLICE_RND_MAX_CURSORS = MongoServiceConfiguration.get().getEagerRndMaxCursors();

    private final Cache<CursorPoolEntryKey, FindIterable<BsonDocument>> cache;
    private final LoadingCache<CursorPoolEntryKey, Long> collSizes;

    private CursorPool() {
        cache = CacheFactory.createLocalCache(POOL_SIZE, Cache.EXPIRE_POLICY.AFTER_READ, TTL);

        collSizes = CacheFactory.createLocalLoadingCache(100,
            org.restheart.cache.Cache.EXPIRE_POLICY.AFTER_WRITE,
            60 * 1000,
            (CursorPoolEntryKey key) -> collections.getCollectionSize(key.session(), key.collection(), key.filter())
        );

        if (LOGGER.isTraceEnabled()) {
            // print stats every 1 minute
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                getCacheSizes().forEach((s, c) -> {
                    LOGGER.debug("db cursor pool size: {}\t{}", s, c);
                });

                LOGGER.trace("db cursor pool entries: {}", cache.asMap().keySet());
            }, 1, 1, TimeUnit.MINUTES);
        }
    }

    /**
     *
     * @param key
     * @param allocationPolicy
     * @return
     */
    public synchronized SkippedFindIterable get(CursorPoolEntryKey key, EAGER_CURSOR_ALLOCATION_POLICY allocationPolicy) {
        if (key.skipped() < SKIP_SLICE_LINEAR_WIDTH) {
            LOGGER.trace("{} cursor to reuse found with less skips than SKIP_SLICE_LINEAR_WIDTH {}", ansi().fg(GREEN).bold().a("no").reset().toString(), SKIP_SLICE_LINEAR_WIDTH);
            return null;
        }

        // return the dbcursor with the closest skips to the request
        var _bestKey = cache.asMap().keySet().stream()
            .filter(cursorsPoolFilterGte(key))
            .sorted(Comparator.comparingInt(CursorPoolEntryKey::skipped).reversed())
            .findFirst();

        SkippedFindIterable ret;

        if (_bestKey.isPresent()) {
            var _dbcur = cache.get(_bestKey.get());

            if (_dbcur != null && _dbcur.isPresent()) {
                ret = new SkippedFindIterable(_dbcur.get(), _bestKey.get().skipped());
                cache.invalidate(_bestKey.get());

                LOGGER.debug("{} cursor in pool. id {}, saving {} skips", ansi().fg(GREEN).bold().a("found").reset().toString(), _bestKey.get().cursorId(), key.skipped(), _bestKey.get().skipped());
            } else {
                ret = null;

                LOGGER.debug("{} cursor in pool.", ansi().fg(RED).bold().a("no").reset().toString());
            }
        } else {
            ret = null;

            LOGGER.debug(ansi().fg(RED).bold().a("no").reset().toString() + " cursor in pool.");
        }

        return ret;
    }

    void populateCache(CursorPoolEntryKey key, EAGER_CURSOR_ALLOCATION_POLICY allocationPolicy) {
        if (allocationPolicy == EAGER_CURSOR_ALLOCATION_POLICY.LINEAR) {
            populateCacheLinear(key);
        } else if (allocationPolicy == EAGER_CURSOR_ALLOCATION_POLICY.RANDOM) {
            populateCacheRandom(key);
        }
    }

    private void populateCacheLinear(CursorPoolEntryKey key) {
        if (key.skipped() < SKIP_SLICE_LINEAR_WIDTH) {
            return;
        }

        int firstSlice = key.skipped() / SKIP_SLICE_LINEAR_WIDTH;

        try {
            POOL_POPULATOR.submit(() -> {
                int slice = firstSlice;

                for (int tohave : SKIP_SLICES_HEIGHTS) {
                    int sliceSkips = slice * SKIP_SLICE_LINEAR_WIDTH - SKIP_SLICE_LINEAR_DELTA;

                    var sliceKey = new CursorPoolEntryKey(
                        key.session(),
                        key.collection(),
                        key.sort(),
                        key.filter(),
                        key.keys(),
                        key.hint(),
                        sliceSkips,
                        -1);

                    long existing = getSliceHeight(sliceKey);

                    long tocreate = tohave - existing;

                    for (long cont = tocreate; cont > 0; cont--) {
                        // create the first cursor
                        var cursor = collections.findIterable(
                            key.session(),
                            key.collection(),
                            key.sort(),
                            key.filter(),
                            key.hint(),
                            key.keys());

                        cursor.skip(sliceSkips);

                        cursor.iterator(); // this forces the actual skipping

                        var newkey = new CursorPoolEntryKey(
                            key.session(),
                            key.collection(),
                            key.sort(),
                            key.filter(),
                            key.hint(),
                            key.keys(),
                            sliceSkips,
                            System.nanoTime());

                        cache.put(newkey, cursor);

                        LOGGER.debug("{} cursor in pool: {}", ansi().fg(YELLOW).bold().a("new").reset().toString(), newkey);
                    }

                    slice++;
                }
            });
        } catch (RejectedExecutionException rej) {
            // this happens if the thread executor (whose pool size is 1)
            // is already creating a cursor
            LOGGER.trace("creation of new cursor pool {}", ansi().fg(RED).bold().a("rejected").reset().toString());
        }
    }

    private void populateCacheRandom(CursorPoolEntryKey key) {
        try {
            POOL_POPULATOR.submit(() -> {
                var size = collSizes.getLoading(key).get();

                int sliceWidht;
                int slices;
                int totalSlices = size.intValue() / SKIP_SLICE_RND_MIN_WIDTH + 1;

                if (totalSlices <= SKIP_SLICE_RND_MAX_CURSORS) {
                    slices = totalSlices;
                    sliceWidht = SKIP_SLICE_RND_MIN_WIDTH;
                } else {
                    slices = SKIP_SLICE_RND_MAX_CURSORS;
                    sliceWidht = size.intValue() / slices;
                }

                for (int slice = 1; slice < slices; slice++) {
                    int sliceSkips = slice * sliceWidht;

                    var sliceKey = CursorPoolEntryKey.clone(key);

                    LOGGER.debug("{} cursor in pool: {}", ansi().fg(YELLOW).bold().a("new").reset() .toString(), sliceKey);

                    long existing = getSliceHeight(sliceKey);

                    if (existing == 0) {
                        var cursor = collections.findIterable(
                            key.session(),
                            key.collection(),
                            key.sort(),
                            key.filter(),
                            key.hint(),
                            key.keys())
                            .skip(sliceSkips);

                        cursor.iterator(); // this forces the actual skipping

                        var newkey = new CursorPoolEntryKey(
                            key.session(),
                            key.collection(),
                            key.sort(),
                            key.filter(),
                            key.hint(),
                            key.keys(),
                            sliceSkips,
                            System.nanoTime());
                        cache.put(newkey, cursor);

                        LOGGER.debug("{} cursor in pool (copied): {}", ansi().fg(YELLOW).bold().a("new").reset().toString(), sliceKey);
                    }
                }
            });
        } catch (RejectedExecutionException rej) {
            LOGGER.debug("populate cursor pool {}", ansi().fg(RED).bold().a("rejected").reset().toString());
        }

    }

    private long getSliceHeight(CursorPoolEntryKey key) {
        long ret = cache.asMap().keySet().stream().filter(cursorsPoolFilterEq(key)).count();

        LOGGER.trace("cursor in pool with skips {} are {}", key.skipped(), ret);

        return ret;
    }

    private Predicate<? super CursorPoolEntryKey> cursorsPoolFilterEq( CursorPoolEntryKey requestCursor) {
        return poolCursor
            -> Objects.equals(poolCursor.collection().getNamespace(), requestCursor.collection().getNamespace())
            && Objects.equals(poolCursor.filter(), requestCursor.filter())
            && Objects.equals(poolCursor.sort(), requestCursor.sort())
            && Objects.equals(poolCursor.keys(), requestCursor.keys())
            && poolCursor.skipped() == requestCursor.skipped();
    }

    private Predicate<? super CursorPoolEntryKey> cursorsPoolFilterGte(
            CursorPoolEntryKey requestCursor) {
        return poolCursor
            -> Objects.equals(poolCursor.collection().getNamespace(), requestCursor.collection().getNamespace())
            && Objects.equals(poolCursor.filter(), requestCursor.filter())
            && Objects.equals(poolCursor.sort(), requestCursor.sort())
            && Objects.equals(poolCursor.keys(), requestCursor.keys())
            && poolCursor.skipped() <= requestCursor.skipped()
            && requestCursor.skipped() - poolCursor.skipped() <= MIN_SKIP_DISTANCE_PERCENTAGE * requestCursor.skipped();
    }

    private TreeMap<String, Long> getCacheSizes() {
        return new TreeMap<>(cache.asMap()
            .keySet()
            .stream()
            .collect(Collectors.groupingBy(CursorPoolEntryKey::getCacheStatsGroup, Collectors.counting())));
    }

    private static class DBCursorPoolSingletonHolder {
        private static final CursorPool INSTANCE = new CursorPool();

        private DBCursorPoolSingletonHolder() {
        }
    };
}
