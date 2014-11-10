/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
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
 * @author uji
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

    public static LocalCachesSingleton getInstance() {
        return LocalCachesSingletonHolder.INSTANCE;
    }

    private static class LocalCachesSingletonHolder {
        private static final LocalCachesSingleton INSTANCE = new LocalCachesSingleton();
    }

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
            }
            else {
                dbProps = null;
            }
        }
        else {
            try {
                _dbProps = dbPropsCache.getUnchecked(dbName);
            }
            catch (UncheckedExecutionException uex) {
                if (uex.getCause() instanceof MongoException) {
                    throw (MongoException) uex.getCause();
                }
                else {
                    throw uex;
                }
            }

            if (_dbProps != null && _dbProps.isPresent()) {
                dbProps = _dbProps.get();
                dbProps.put("_db-props-cached", false);
            }
            else {
                dbProps = null;
            }
        }

        return dbProps;
    }

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
            }
            else {
                collProps = null;
            }
        }
        else {
            try {
                _collProps = collectionPropsCache.getUnchecked(dbName + SEPARATOR + collName);
            }
            catch (UncheckedExecutionException uex) {
                if (uex.getCause() instanceof MongoException) {
                    throw (MongoException) uex.getCause();
                }
                else {
                    throw uex;
                }
            }

            if (_collProps.isPresent()) {
                collProps = _collProps.get();
                collProps.put("_collection-props-cached", false);
            }
            else {
                collProps = null;
            }
        }

        return collProps;
    }

    public void invalidateDb(String dbName) {
        if (enabled && dbPropsCache != null) {
            dbPropsCache.invalidate(dbName);
        }
    }

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
