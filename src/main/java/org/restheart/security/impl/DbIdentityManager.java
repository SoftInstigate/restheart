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
package org.restheart.security.impl;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashSet;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.db.MongoDBClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public final class DbIdentityManager extends AbstractSimpleSecurityManager implements IdentityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbIdentityManager.class);

    private DBCollection mongoColl;

    private String db;
    private String coll;
    private Boolean cacheEnabled = false;
    private Long cacheSize = 1000l; // 1000 entries
    private Long cacheTTL = 60 * 1000l; // 1 minute
    private Cache.EXPIRE_POLICY cacheExpirePolicy = Cache.EXPIRE_POLICY.AFTER_WRITE;

    private LoadingCache<String, SimpleAccount> cache = null;

    /**
     *
     * @param arguments
     * @throws java.io.FileNotFoundException
     */
    public DbIdentityManager(Map<String, Object> arguments) throws FileNotFoundException {
        init(arguments, "dbim");

        if (this.cacheEnabled) {
            this.cache = CacheFactory.createLocalLoadingCache(this.cacheSize, this.cacheExpirePolicy, this.cacheTTL, (String key) -> {
                        return this.findAccount(key);
                    });
        }

        MongoClient mongoClient = MongoDBClientSingleton.getInstance().getClient();

        if (!mongoClient.getDatabaseNames().contains(this.db)) {
            throw new IllegalArgumentException("error configuring the DbIdentityManager. The specified db does not exist: " + db);
        }

        DB mongoDb = mongoClient.getDB(this.db);

        if (!mongoDb.collectionExists(this.coll)) {
            throw new IllegalArgumentException("error configuring the DbIdentityManager. The specified collection does not exist: " + coll);
        }

        this.mongoColl = mongoDb.getCollection(coll);
    }

    @Override
    Consumer<? super Map<String, Object>> consumeConfiguration() {
        return ci -> {
            Object _db = ci.get("db");
            Object _coll = ci.get("coll");

            Object _cacheEnabled = ci.get("cache-enabled");
            Object _cacheSize = ci.get("cache-size");
            Object _cacheTTL = ci.get("cache-ttl");
            Object _cacheExpirePolicy = ci.get("cache-expire-policy");

            if (_db == null || !(_db instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. missing db property");
            }

            if (_coll == null || !(_coll instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. missing coll property");
            }

            if (_cacheEnabled != null && !(_cacheEnabled instanceof Boolean)) {
                throw new IllegalArgumentException("wrong configuration file format. cache-enabled must be a boolean");
            }

            if (_cacheSize != null && !(_cacheSize instanceof Long || _cacheSize instanceof Integer)) {
                throw new IllegalArgumentException("wrong configuration file format. cache-size must be a number");
            }

            if (_cacheTTL != null && !(_cacheTTL instanceof Long || _cacheTTL instanceof Integer)) {
                throw new IllegalArgumentException("wrong configuration file format. cache-ttl must be a number (of milliseconds)");
            }

            if (_cacheExpirePolicy != null && !(_cacheExpirePolicy instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. cache-expire-policy valid values are " + Arrays.toString(Cache.EXPIRE_POLICY.values()));
            }

            this.db = (String) _db;
            this.coll = (String) _coll;

            if (_cacheEnabled != null) {
                this.cacheEnabled = (Boolean) _cacheEnabled;
            }

            if (_cacheSize != null) {
                if (_cacheSize instanceof Integer) {
                    this.cacheSize = ((Integer)_cacheSize).longValue();
                } else {
                    this.cacheSize = (Long) _cacheSize;
                }
            }

            if (_cacheTTL != null) {
                if (_cacheTTL instanceof Integer) {
                    this.cacheTTL = ((Integer)_cacheTTL).longValue();
                } else {
                    this.cacheTTL = (Long) _cacheTTL;
                }
            }

            if (_cacheExpirePolicy != null) {
                try {
                    this.cacheExpirePolicy = Cache.EXPIRE_POLICY.valueOf((String) _cacheExpirePolicy);
                } catch (IllegalArgumentException iae) {
                    throw new IllegalArgumentException("wrong configuration file format. cache-expire-policy valid values are " + Arrays.toString(Cache.EXPIRE_POLICY.values()));
                }
            }
        };
    }

    @Override
    public Account verify(Account account) {
        return account;
    }

    @Override
    public Account verify(String id, Credential credential) {
        final Account account = getAccount(id);
        return account != null && verifyCredential(account, credential) ? account : null;
    }

    @Override
    public Account verify(Credential credential) {
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential) {
        String id = account.getPrincipal().getName();
        
        SimpleAccount ourAccount = getAccount(id);
        
        if (ourAccount == null)
            return false;
        
        if (credential instanceof PasswordCredential && account instanceof SimpleAccount) {
            char[] password = ((PasswordCredential) credential).getPassword();
            char[] expectedPassword = ourAccount.getCredentials().getPassword();

            return Arrays.equals(password, expectedPassword);
        }
        return false;
    }
    
    private SimpleAccount getAccount(String id) {
        if (cache == null) {
            return findAccount(id);
        } else {
            Optional<SimpleAccount> _account = cache.get(id);
            
            if (_account.isPresent()) {
                return _account.get();
            } else {
                return null;
            }
        }
    }

    private SimpleAccount findAccount(String _id) {
        DBObject query = new BasicDBObject();
        query.put("_id", _id);

        DBObject _account = mongoColl.findOne(query);

        if (_account == null) {
            LOGGER.debug("no account found with _id: {}", _id);
            return null;
        }

        Object _password = _account.get("password");
        Object _roles = _account.get("roles");

        if (_password == null) {
            LOGGER.debug("account with _id: {} does not have password property", _id);
            return null;
        }
        
        if (!(_password instanceof String)) {
            LOGGER.debug("account with _id: {} has the password property, but it is not a String", _id);
            return null;
        }
        
        String password = (String) _password;

        if (_roles == null) {
            LOGGER.debug("account with _id: {} does not have the roles property", _id);
            return null;
        }
        
        if (!(_roles instanceof BasicDBList)) {
            LOGGER.debug("account with _id: {} has the roles property, but it is not an array: {}", _id, _roles.toString());
            return null;
        }
        
        Set<String> roles = new HashSet<>();
        
        ((BasicDBList)_roles).forEach(el -> {
            if ((el != null && el instanceof String)) {
                roles.add((String)el);
            } else {
                LOGGER.debug("account with _id: {} has a role that is not a string and it is not taken into account: {}", _id, el);
            }
        });
        
        return new SimpleAccount(_id, password.toCharArray(), roles);
    }
}
