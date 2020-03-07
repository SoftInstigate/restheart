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
package org.restheart.handlers.injectors;

import com.mongodb.MongoException;
import java.util.Optional;
import org.bson.BsonDocument;
import org.restheart.Configuration;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.db.Database;
import org.restheart.db.DatabaseImpl;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LocalCachesSingleton {

    private static final String SEPARATOR = "_@_@_";
    private static boolean initialized = false;

    private static long ttl = 1_000;
    private static boolean enabled = false;
    private static final long MAX_CACHE_SIZE = 1_000;

    /**
     *
     * @param conf
     */
    public static void init(Configuration conf) {
        ttl = conf.getLocalCacheTtl();
        enabled = conf.isLocalCacheEnabled();
        initialized = true;
    }

    /**
     *
     * @return
     */
    public static LocalCachesSingleton getInstance() {
        return LocalCachesSingletonHolder.INSTANCE;
    }

    /**
     * @return the enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }
    private final Database dbsDAO;
    private LoadingCache<String, BsonDocument> dbPropsCache = null;
    private LoadingCache<String, BsonDocument> collectionPropsCache = null;

    /**
     * Default ctor
     */
    private LocalCachesSingleton(DatabaseImpl dbsDAO) {
        this.dbsDAO = dbsDAO;
        setup();
    }

    private void setup() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        if (enabled) {
            this.dbPropsCache = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE, Cache.EXPIRE_POLICY.AFTER_WRITE, ttl,
                    (String key) -> {
                        return this.dbsDAO.getDatabaseProperties(
                                null, // no client session 
                                key);
                    });

            this.collectionPropsCache = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE, Cache.EXPIRE_POLICY.AFTER_WRITE, ttl,
                    (String key) -> {
                        String[] dbNameAndCollectionName = key.split(SEPARATOR);
                        return this.dbsDAO
                                .getCollectionProperties(
                                        null, // no client session 
                                        dbNameAndCollectionName[0],
                                        dbNameAndCollectionName[1]);
                    });
        }
    }

    /**
     *
     * @param dbName
     * @return
     */
    public BsonDocument getDBProperties(String dbName) {
        if (!enabled) {
            throw new IllegalStateException("tried to use disabled cache");
        }

        Optional<BsonDocument> _dbProps = dbPropsCache.get(dbName);

        if (_dbProps != null) {
            if (_dbProps.isPresent()) {
                return _dbProps.get();
            } else {
                return null;
            }
        } else {
            try {
                _dbProps = dbPropsCache.getLoading(dbName);
            } catch (Throwable uex) {
                if (uex.getCause() instanceof MongoException) {
                    throw new RuntimeException(uex.getCause());
                } else {
                    throw uex;
                }
            }

            if (_dbProps != null && _dbProps.isPresent()) {
                return _dbProps.get();
            } else {
                return null;
            }
        }
    }

    /**
     *
     * @param dbName
     * @param collName
     * @return
     */
    public BsonDocument getCollectionProperties(String dbName, String collName) {
        if (!enabled) {
            throw new IllegalStateException("tried to use disabled cache");
        }

        Optional<BsonDocument> _collProps = collectionPropsCache.get(dbName + SEPARATOR + collName);

        if (_collProps != null) {
            if (_collProps.isPresent()) {
                return _collProps.get();
            } else {
                return null;
            }
        } else {
            try {
                _collProps = collectionPropsCache.getLoading(dbName + SEPARATOR + collName);
            } catch (Throwable uex) {
                if (uex.getCause() instanceof MongoException) {
                    throw new RuntimeException(uex.getCause());
                } else {
                    throw uex;
                }
            }

            if (_collProps.isPresent()) {
                return _collProps.get();
            } else {
                return null;
            }
        }
    }

    /**
     *
     * @param dbName
     */
    public void invalidateDb(String dbName) {
        if (enabled && dbPropsCache != null) {
            dbPropsCache.invalidate(dbName);
            collectionPropsCache.asMap().keySet().stream().filter(k -> k.startsWith(dbName + SEPARATOR)).forEach(k -> collectionPropsCache.invalidate(k));
        }
    }

    /**
     *
     * @param dbName
     * @param collName
     */
    public void invalidateCollection(String dbName, String collName) {
        if (enabled && collectionPropsCache != null) {
            collectionPropsCache.invalidate(dbName + SEPARATOR + collName);
        }
    }

    private static class LocalCachesSingletonHolder {

        private static final LocalCachesSingleton INSTANCE = new LocalCachesSingleton(new DatabaseImpl());

        private LocalCachesSingletonHolder() {
        }
    }
}
