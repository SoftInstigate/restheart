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
package com.softinstigate.restheart.handlers.injectors;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.softinstigate.restheart.Configuration;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.DBDAO;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Andrea Di Cesare
 */
public class LocalCachesSingleton {
    private static final String SEPARATOR = "_@_@_";

    private static boolean initialized = false;

    private LoadingCache<String, Optional<DBObject>> dbPropsCache = null;
    private LoadingCache<String, Optional<DBObject>> collectionPropsCache = null;

    private static long ttl = 1000;
    private static boolean enabled = false;
    private static final long maxCacheSize = 1000;

    private LocalCachesSingleton() {
        setup();
    }

    /**
     *
     * @param conf
     */
    public static void init(Configuration conf) {
        ttl = conf.getLocalCacheTtl();
        enabled = conf.isLocalCacheEnabled();
        initialized = true;
    }

    private void setup() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        CacheBuilder builder = CacheBuilder.newBuilder();

        builder.maximumSize(maxCacheSize);

        if (ttl > 0) {
            builder.expireAfterWrite(ttl, TimeUnit.MILLISECONDS);
        }

        if (enabled) {
            this.dbPropsCache = builder.build(
                    new CacheLoader<String, Optional<DBObject>>() {
                        @Override
                        public Optional<DBObject> load(String key) throws Exception {
                            return Optional.ofNullable(DBDAO.getDbProps(key));
                        }
                    });

            this.collectionPropsCache = builder.build(
                    new CacheLoader<String, Optional<DBObject>>() {
                        @Override
                        public Optional<DBObject> load(String key) throws Exception {
                            String[] dbNameAndCollectionName = key.split(SEPARATOR);
                            return Optional.ofNullable(CollectionDAO.getCollectionProps(dbNameAndCollectionName[0], dbNameAndCollectionName[1]));
                        }
                    });
        }
    }

    /**
     *
     * @return
     */
    public static LocalCachesSingleton getInstance() {
        return LocalCachesSingletonHolder.INSTANCE;
    }

    private static class LocalCachesSingletonHolder {
        private static final LocalCachesSingleton INSTANCE = new LocalCachesSingleton();
    }

    /**
     *
     * @param dbName
     * @return
     */
    public DBObject getDBProps(String dbName) {
        if (!enabled) {
            throw new IllegalStateException("tried to use disabled cache");
        }

        DBObject dbProps;

        Optional<DBObject> _dbProps = dbPropsCache.getIfPresent(dbName);

        if (_dbProps != null) {
            if (_dbProps.isPresent()) {
                dbProps = _dbProps.get();
                dbProps.put("_db-props-cached", true);
            } else {
                dbProps = null;
            }
        } else {
            try {
                _dbProps = dbPropsCache.getUnchecked(dbName);
            } catch (UncheckedExecutionException uex) {
                if (uex.getCause() instanceof MongoException) {
                    throw (MongoException) uex.getCause();
                } else {
                    throw uex;
                }
            }

            if (_dbProps != null && _dbProps.isPresent()) {
                dbProps = _dbProps.get();
                dbProps.put("_db-props-cached", false);
            } else {
                dbProps = null;
            }
        }

        return dbProps;
    }

    /**
     *
     * @param dbName
     * @param collName
     * @return
     */
    public DBObject getCollectionProps(String dbName, String collName) {
        if (!enabled) {
            throw new IllegalStateException("tried to use disabled cache");
        }

        DBObject collProps;

        Optional<DBObject> _collProps = collectionPropsCache.getIfPresent(dbName + SEPARATOR + collName);

        if (_collProps != null) {
            if (_collProps.isPresent()) {
                collProps = _collProps.get();
                collProps.put("_collection-props-cached", true);
            } else {
                collProps = null;
            }
        } else {
            try {
                _collProps = collectionPropsCache.getUnchecked(dbName + SEPARATOR + collName);
            } catch (UncheckedExecutionException uex) {
                if (uex.getCause() instanceof MongoException) {
                    throw (MongoException) uex.getCause();
                } else {
                    throw uex;
                }
            }

            if (_collProps.isPresent()) {
                collProps = _collProps.get();
                collProps.put("_collection-props-cached", false);
            } else {
                collProps = null;
            }
        }

        return collProps;
    }

    /**
     *
     * @param dbName
     */
    public void invalidateDb(String dbName) {
        if (enabled && dbPropsCache != null) {
            dbPropsCache.invalidate(dbName);
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

    /**
     * @return the enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }
}
