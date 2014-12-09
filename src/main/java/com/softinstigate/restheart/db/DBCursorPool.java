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

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mongodb.DBCursor;
import java.util.Comparator;
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

    private final Cache<DBCursorPoolEntryKey, DBCursor> cache;

    private static final long TTL = 5; // minutes
    private static final long POOL_SIZE = 100;

    ExecutorService executor = Executors.newSingleThreadExecutor();

    private DBCursorPool() {
        CacheBuilder builder = CacheBuilder.newBuilder()
                .maximumSize(POOL_SIZE)
                .expireAfterAccess(TTL, TimeUnit.MINUTES)
                .recordStats();

        cache = builder.build();

        if (LOGGER.isDebugEnabled()) {
            // print stats every 1 minute
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                LOGGER.debug("stats: {}", stats());
                LOGGER.debug("pool size: {} ", cache.size());
                LOGGER.debug("pool entries: {}", cache.asMap().keySet());
            }, 1, 1, TimeUnit.MINUTES);
        }
    }

    public synchronized SkippedDBCursor lease(DBCursorPoolEntryKey key) {
        // return the dbursor closer to the request
        Optional<DBCursorPoolEntryKey> _bestKey = cache.asMap().keySet().stream().filter(k
                -> Objects.equal(k.getCollection().getDB().getName(), key.getCollection().getDB().getName())
                && Objects.equal(k.getCollection().getName(), key.getCollection().getName())
                && Objects.equal(k.getFilter(), key.getFilter())
                && Objects.equal(k.getSort(), key.getSort())
                && k.getSkipped() <= key.getSkipped()
        ).sorted(Comparator.comparingInt(DBCursorPoolEntryKey::getSkipped).reversed()).findFirst();

        SkippedDBCursor ret;

        if (_bestKey.isPresent()) {
            ret = new SkippedDBCursor(cache.getIfPresent(_bestKey.get()), _bestKey.get().getSkipped());
            cache.invalidate(_bestKey.get());
            
            LOGGER.debug("found cursor to reuse in pool, asked with skipped {} and saving {} seeks", key.getSkipped(), _bestKey.get().getSkipped());
        } else {
            LOGGER.debug("no cursor to reuse found with skipped {}.", key.getSkipped(), cache.asMap().keySet());
            cache.getIfPresent(new DBCursorPoolEntryKey(null, null, null, -1)); // just to update the cache missed stats
            ret = null;
        }

        executor.submit(() -> {
            DBCursor cursor = CollectionDAO.getCollectionDBCursor(key.getCollection(), key.getSort(), key.getFilter());
            int s = key.getSkipped() - 1000 >= 0 ? key.getSkipped() - 1000 : 0;

            cursor.skip(s);

            DBCursorPoolEntryKey newkey = new DBCursorPoolEntryKey(key.getCollection(), key.getSort(), key.getFilter(), s);

            cache.put(newkey, cursor);
            LOGGER.debug("created new cursor in pool: {}", newkey);
        });

        return ret;
    }

    /*
    public void release(DBCursorPoolEntryKey key, DBCursor cursor) {
        cache.put(key, cursor);
        LOGGER.debug("released cursor {} to the pool.", key);
    }*/

    public String stats() {
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
