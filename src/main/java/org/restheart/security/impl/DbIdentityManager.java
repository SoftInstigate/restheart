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
package org.restheart.security.impl;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.mindrot.jbcrypt.BCrypt;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.db.MongoDBClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public final class DbIdentityManager
        extends AbstractSimpleSecurityManager
        implements IdentityManager {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(DbIdentityManager.class);

    private MongoCollection<BsonDocument> mongoColl;

    private String db;
    private String coll;
    private String propertyNameId = "_id";
    private String propertyNamePassword = "password";
    private String propertyNameRoles = "roles";
    private Boolean bcryptHashedPassword = false;
    private Boolean createUser = false;
    private BsonDocument createUserDocument = null;
    private Boolean cacheEnabled = false;
    private Long cacheSize = 1_000l; // 1000 entries
    private Long cacheTTL = 60 * 1_000l; // 1 minute
    private Cache.EXPIRE_POLICY cacheExpirePolicy
            = Cache.EXPIRE_POLICY.AFTER_WRITE;

    private LoadingCache<String, SimpleAccount> cache = null;

    /**
     *
     * @param arguments
     * @throws java.io.FileNotFoundException
     */
    public DbIdentityManager(Map<String, Object> arguments)
            throws FileNotFoundException {
        init(arguments, "dbim");

        if (this.cacheEnabled) {
            this.cache = CacheFactory.createLocalLoadingCache(
                    this.cacheSize,
                    this.cacheExpirePolicy,
                    this.cacheTTL, (String key) -> {
                        return this.findAccount(key);
                    });
        }
        
        MongoClient mongoClient = MongoDBClientSingleton
                .getInstance().getClient();

        MongoDatabase mongoDb = mongoClient.getDatabase(this.db);

        this.mongoColl = mongoDb.getCollection(coll, BsonDocument.class);
        
        if (this.createUser) {
            LOGGER.trace("create user option enabled");
            if (this.mongoColl.count() < 1) {         
                this.mongoColl.insertOne(this.createUserDocument);
                LOGGER.info("no user found. created default user with _id {}", this.createUserDocument.get("_id"));
            } else {
                LOGGER.trace("not creating default user since users exist");
            }
        }
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
            Object _bcryptHashedPassword = ci.get("bcrypt-hashed-password");
            Object _createUser = ci.get("create-user");
            Object _createUserDocument = ci.get("create-user-document");

            Object _propertyNameId = ci.get("prop-name-id");
            Object _propertyNamePassword = ci.get("prop-name-password");
            Object _propertyNameRoles = ci.get("prop-name-roles");

            if (_db == null || !(_db instanceof String)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "missing db property");
            }

            if (_coll == null || !(_coll instanceof String)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "missing coll property");
            }

            if (_cacheEnabled != null && !(_cacheEnabled instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "cache-enabled must be a boolean");
            }

            if (_cacheSize != null
                    && !(_cacheSize instanceof Long
                    || _cacheSize instanceof Integer)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "cache-size must be a number");
            }

            if (_cacheTTL != null
                    && !(_cacheTTL instanceof Long
                    || _cacheTTL instanceof Integer)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "cache-ttl must be a number (of milliseconds)");
            }

            if (_cacheExpirePolicy != null
                    && !(_cacheExpirePolicy instanceof String)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "cache-expire-policy valid values are "
                        + Arrays.toString(Cache.EXPIRE_POLICY.values()));
            }

            if (_bcryptHashedPassword != null
                    && !(_bcryptHashedPassword instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "bcrypt-hashed-password must be a boolean");
            }

            if (_createUser != null
                    && !(_createUser instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "create-user must be a boolean");
            }

            if (_createUserDocument != null
                    && !(_createUserDocument instanceof String)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "create-user-document must be a json document");
            }

            if (_propertyNameId != null
                    && !(_propertyNameId instanceof String)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "prop-name-id must be a string");
            }

            if (_propertyNamePassword != null
                    && !(_propertyNamePassword instanceof String)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "prop-name-password must be a string");
            }

            if (_propertyNameRoles != null
                    && !(_propertyNameRoles instanceof String)) {
                throw new IllegalArgumentException(
                        "wrong configuration file format. "
                        + "prop-name-roles must be a string");
            }

            this.db = (String) _db;
            this.coll = (String) _coll;

            if (_cacheEnabled != null) {
                this.cacheEnabled = (Boolean) _cacheEnabled;
            }

            if (_cacheSize != null) {
                if (_cacheSize instanceof Integer) {
                    this.cacheSize = ((Integer) _cacheSize).longValue();
                } else {
                    this.cacheSize = (Long) _cacheSize;
                }
            }

            if (_cacheTTL != null) {
                if (_cacheTTL instanceof Integer) {
                    this.cacheTTL = ((Integer) _cacheTTL).longValue();
                } else {
                    this.cacheTTL = (Long) _cacheTTL;
                }
            }

            if (_cacheExpirePolicy != null) {
                try {
                    this.cacheExpirePolicy = Cache.EXPIRE_POLICY
                            .valueOf((String) _cacheExpirePolicy);
                } catch (IllegalArgumentException iae) {
                    throw new IllegalArgumentException(
                            "wrong configuration file format. "
                            + "cache-expire-policy valid values are "
                            + Arrays.toString(Cache.EXPIRE_POLICY.values()));
                }
            }

            if (_bcryptHashedPassword != null) {
                this.bcryptHashedPassword = (Boolean) _bcryptHashedPassword;
            }

            if (_createUser != null) {
                this.createUser = (Boolean) _createUser;
            }

            if (this.createUser && _createUserDocument != null) {
                try {
                    this.createUserDocument
                            = BsonDocument.parse((String) _createUserDocument);
                } catch (Exception ex) {
                    throw new IllegalArgumentException(
                            "wrong configuration file format. "
                            + "create-user-document must be a json document", ex);
                }
            }

            if (_propertyNameId != null) {
                this.propertyNameId = (String) _propertyNameId;
            }

            if (_propertyNamePassword != null) {
                this.propertyNamePassword = (String) _propertyNamePassword;
            }

            if (_propertyNameRoles != null) {
                this.propertyNameRoles = (String) _propertyNameRoles;
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
        return account != null
                && verifyCredential(account, credential) ? account : null;
    }

    @Override
    public Account verify(Credential credential) {
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential) {
        String id = account.getPrincipal().getName();

        SimpleAccount ourAccount = getAccount(id);

        if (ourAccount == null) {
            return false;
        }

        if (credential instanceof PasswordCredential
                && account instanceof SimpleAccount) {
            char[] password = ((PasswordCredential) credential).getPassword();
            char[] expected = ourAccount.getCredentials().getPassword();

            return checkPassword(
                    this.bcryptHashedPassword,
                    password,
                    expected);
        }
        return false;
    }

    static boolean checkPassword(boolean hashed, char[] password, char[] expected) {
        if (hashed) {
            try {
                return BCrypt.checkpw(
                        new String(password),
                        new String(expected));
            } catch (Throwable t) {
                return false;
            }
        } else {
            return Arrays.equals(password, expected);
        }
    }

    private SimpleAccount getAccount(String id) {
        if (cache == null) {
            return findAccount(id);
        } else {
            Optional<SimpleAccount> _account = cache.getLoading(id);

            if (_account != null && _account.isPresent()) {
                return _account.get();
            } else {
                return null;
            }
        }
    }

    private SimpleAccount findAccount(String id) {
        Bson query = eq(this.propertyNameId, id);

        FindIterable<BsonDocument> result = mongoColl
                .find(query)
                .limit(1);

        if (result == null || !result.iterator().hasNext()) {
            LOGGER.debug("no account found with id: {}", id);
            return null;
        }

        BsonDocument _account = result.iterator().next();

        if (!_account.containsKey(this.propertyNamePassword)) {
            LOGGER.error("account with id: {} does not have password {}",
                    id,
                    this.propertyNamePassword);
            return null;
        }

        BsonValue _password = _account.get(this.propertyNamePassword);

        if (_password == null || !_password.isString()) {
            LOGGER.debug(
                    "account with id: {} "
                    + "has an invalid password (not string): {}",
                    id, _password);
            return null;
        }

        String password = _password.asString().getValue();

        if (!_account.containsKey(this.propertyNameRoles)) {
            LOGGER.error("account with id: {} does not have {} property",
                    id,
                    this.propertyNameRoles);
            return null;
        }

        BsonValue _roles = _account.get(this.propertyNameRoles);

        if (_roles == null || !_roles.isArray()) {
            LOGGER.debug(
                    "account with id: {} has an invalid roles (not array): {}",
                    id, _roles);
            return null;
        }

        Set<String> roles = new HashSet<>();

        List<BsonValue> __roles = _roles.asArray().getValues();

        __roles.forEach(el -> {
            if (el != null && el.isString()) {
                roles.add(el.asString().getValue());
            } else {
                LOGGER.debug(
                        "account with _d: {} "
                        + "has a not string role: {} ; ignoring it",
                        id, el);
            }
        });

        return new SimpleAccount(id, password.toCharArray(), roles);
    }
}
