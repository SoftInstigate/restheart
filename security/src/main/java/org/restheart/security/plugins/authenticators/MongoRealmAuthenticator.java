/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.security.plugins.authenticators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mongodb.MongoClient;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.util.HexConverter;
import io.undertow.util.HttpString;
import static io.undertow.util.RedirectBuilder.UTF_8;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;
import org.restheart.ConfigurationException;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.idm.PwdCredentialAccount;
import org.restheart.security.utils.MongoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "mongoRealmAuthenticator",
        description = "authenticate requests against client credentials stored in mongodb")
public class MongoRealmAuthenticator implements Authenticator {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(MongoRealmAuthenticator.class);

    public static final String X_FORWARDED_ACCOUNT_ID = "rhAuthenticator";
    public static final String X_FORWARDED_ROLE = "RESTHeart";

    private String propId = "_id";
    String usersDb;
    String usersCollection;
    String propPassword = "password";
    private String jsonPathRoles = "$.roles";
    private Boolean bcryptHashedPassword = false;
    Integer bcryptComplexity = 12;
    private Boolean createUser = false;
    private BsonDocument createUserDocument = null;
    private Boolean cacheEnabled = false;
    private Integer cacheSize = 1_000; // 1000 entries
    private Integer cacheTTL = 60 * 1_000; // 1 minute
    private Cache.EXPIRE_POLICY cacheExpirePolicy
            = Cache.EXPIRE_POLICY.AFTER_WRITE;

    private LoadingCache<String, PwdCredentialAccount> USERS_CACHE = null;

    private static final transient Cache<String, String> USERS_PWDS_CACHE
            = CacheFactory.createLocalCache(
                    1_000l,
                    Cache.EXPIRE_POLICY.AFTER_READ,
                    20 * 60 * 1_000l);

    private PluginsRegistry registry;
    private MongoClient mclient;

    @InjectConfiguration
    public void setConf(Map<String, Object> args) {
        this.setUsersDb(argValue(args, "users-db"));
        this.usersCollection = argValue(args, "users-collection");
        this.cacheEnabled = argValue(args, "cache-enabled");
        this.cacheSize = argValue(args, "cache-size");
        this.cacheTTL = argValue(args, "cache-ttl");

        String _cacheExpirePolicy = argValue(args, "cache-expire-policy");
        if (_cacheExpirePolicy != null) {
            try {
                this.cacheExpirePolicy = Cache.EXPIRE_POLICY
                        .valueOf((String) _cacheExpirePolicy);
            } catch (IllegalArgumentException iae) {
                throw new ConfigurationException(
                        "wrong configuration file format. "
                        + "cache-expire-policy valid values are "
                        + Arrays.toString(Cache.EXPIRE_POLICY.values()));
            }
        }

        this.bcryptHashedPassword = argValue(args, "bcrypt-hashed-password");

        this.bcryptComplexity = argValue(args, "bcrypt-complexity");

        this.createUser = argValue(args, "create-user");
        String _createUserDocument = argValue(args, "create-user-document");

        // check createUserDocument
        try {
            this.createUserDocument = BsonDocument.parse(_createUserDocument);
        } catch (JsonParseException ex) {
            throw new ConfigurationException(
                    "wrong configuration file format. "
                    + "create-user-document must be a json document", ex);
        }

        this.propId = argValue(args, "prop-id");

        if (this.propId.startsWith("$")) {
            throw new ConfigurationException("prop-id must be "
                    + "a root property name not a json path expression. "
                    + "It can use the dot notation.");
        }

        this.propPassword = argValue(args, "prop-password");

        if (this.propPassword.contains(".")) {
            throw new ConfigurationException("prop-password must be "
                    + "a root level property and cannot contain the char '.'");
        }

        this.jsonPathRoles = argValue(args, "json-path-roles");
    }

    @InjectMongoClient
    public void setMongoClient(MongoClient mclient) {
        this.mclient = mclient;

        if (this.cacheEnabled) {
            this.USERS_CACHE = CacheFactory.createLocalLoadingCache(
                    this.cacheSize,
                    this.cacheExpirePolicy,
                    this.cacheTTL, (String key) -> {
                        return findAccount(accountIdTrasformer(key));
                    });
        }

        if (!checkUserCollection()) {
            LOGGER.error("Users collection does not exist and could not be created");
        } else if (this.createUser) {
            LOGGER.trace("Create user option enabled");

            if (countAccounts() < 1) {
                createDefaultAccount();
                LOGGER.info("No user found. Created default user with _id {}",
                        this.createUserDocument.get(this.propId));
            } else {
                LOGGER.trace("Not creating default user since users exist");
            }
        }
    }

    @InjectPluginsRegistry
    public void setRegistry(PluginsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Account verify(Account account) {
        return account;
    }

    @Override
    public Account verify(String id, Credential credential) {
        if (credential == null) {
            LOGGER.debug("cannot verify null credential");
            return null;
        }

        PwdCredentialAccount ref = getAccount(id);

        boolean verified;

        if (credential instanceof PasswordCredential) {
            verified = verifyPasswordCredential(
                    ref,
                    (PasswordCredential) credential);
        } else if (credential instanceof DigestCredential) {
            verified = verifyDigestCredential(
                    ref,
                    (DigestCredential) credential);
        } else {
            LOGGER.warn("mongoRealmAuthenticator does not support credential of type {}",
                    credential.getClass().getSimpleName());
            verified = false;
        }

        if (verified) {
            updateAuthTokenCache(ref);
            return ref;
        } else {
            return null;
        }
    }

    /**
     * @return the bcryptComplexity
     */
    public Integer getBcryptComplexity() {
        return bcryptComplexity;
    }

    /**
     * @param bcryptComplexity the bcryptComplexity to set
     */
    public void setBcryptComplexity(Integer bcryptComplexity) {
        this.bcryptComplexity = bcryptComplexity;
    }

    /**
     *
     * @param expectedPassword
     * @param credential
     * @return true if credential verifies successfully against ref account
     */
    private boolean verifyPasswordCredential(
            PwdCredentialAccount ref,
            PasswordCredential credential) {
        if (ref == null
                || ref.getPrincipal() == null
                || ref.getPrincipal().getName() == null
                || ref.getCredentials() == null
                || ref.getCredentials().getPassword() == null
                || credential == null || credential.getPassword() == null) {
            return false;
        }

        return checkPassword(
                ref.getPrincipal().getName(),
                this.bcryptHashedPassword,
                credential.getPassword(),
                ref.getCredentials().getPassword());
    }

    /**
     *
     * @param principalName
     * @param expectedPassword
     * @param credential
     * @return true if password verified successfully
     */
    private boolean verifyDigestCredential(
            PwdCredentialAccount ref,
            DigestCredential credential) {
        if (this.bcryptHashedPassword) {
            LOGGER.error("Digest authentication cannot support bcryped stored "
                    + "password, consider using basic authetication over TLS");
            return false;
        }

        if (ref == null
                || ref.getCredentials() == null
                || ref.getCredentials().getPassword() == null
                || ref.getPrincipal() == null
                || ref.getPrincipal().getName() == null
                || credential == null) {
            return false;
        }

        try {
            MessageDigest digest = credential.getAlgorithm().getMessageDigest();

            digest.update(ref.getPrincipal().getName().getBytes(UTF_8));
            digest.update((byte) ':');
            digest.update(credential.getRealm().getBytes(UTF_8));
            digest.update((byte) ':');
            digest.update(new String(ref.getCredentials().getPassword()).getBytes(UTF_8));

            byte[] ha1 = HexConverter.convertToHexBytes(digest.digest());

            return credential.verifyHA1(ha1);
        } catch (NoSuchAlgorithmException ne) {
            LOGGER.error(ne.getMessage(), ne);
            return false;
        } catch (UnsupportedEncodingException usc) {
            LOGGER.error(usc.getMessage(), usc);
            return false;
        }
    }

    @Override
    public Account verify(Credential credential) {
        return null;
    }

    static boolean checkPassword(String username,
            boolean hashed,
            char[] password,
            char[] expected) {
        if (hashed) {
            if (username == null || password == null || expected == null) {
                return false;
            }

            var _password = new String(password);
            var _expected = new String(expected);

            // speedup bcrypted pwd check if already checked.
            // bcrypt check is very CPU intensive by design.
            var _cachedPwd = USERS_PWDS_CACHE.get(username.concat(_expected));

            if (_cachedPwd != null
                    && _cachedPwd.isPresent()
                    && _cachedPwd.get().equals(_password)) {
                return true;
            }

            try {
                boolean check = BCrypt.checkpw(_password, _expected);

                if (check) {
                    USERS_PWDS_CACHE.put(username.concat(_expected), _password);
                    return true;
                } else {
                    return false;
                }
            } catch (Throwable t) {
                USERS_PWDS_CACHE.invalidate(username.concat(_expected));
                LOGGER.warn("Error checking bcryped pwd hash", t);
                return false;
            }
        } else {
            return Arrays.equals(password, expected);
        }
    }

    private PwdCredentialAccount getAccount(String id) {
        if (this.mclient == null) {
            LOGGER.error("Cannot find account: mongo service is not enabled.");
            return null;
        }

        if (USERS_CACHE == null) {
            return findAccount(this.accountIdTrasformer(id));
        } else {
            Optional<PwdCredentialAccount> _account = USERS_CACHE.getLoading(id);

            if (_account != null && _account.isPresent()) {
                return _account.get();
            } else {
                return null;
            }
        }
    }

    /**
     * Override this method to trasform the account id. By default it returns
     * the id without any transformation. For example, it could be overridden to
     * force the id to be lowercase.
     *
     * @param id the account id
     * @return the trasformed account Id (default is identity)
     */
    protected String accountIdTrasformer(final String id) {
        return id;
    }

    /**
     * if client authenticates passing the real credentials, update the account
     * in the auth-token cache, otherwise the client authenticating with the
     * auth-token will not see roles updates until the cache expires (by default
     * TTL is 15 minutes after last request)
     *
     * @param account
     */
    private void updateAuthTokenCache(PwdCredentialAccount account) {
        try {
            var _tm = registry.getTokenManager();

            if (_tm != null) {
                var tm = _tm.getInstance();

                if (tm.get(account) != null) {
                    tm.update(account);
                }
            }
        } catch (ConfigurationException pce) {
            LOGGER.warn("error getting the token manager", pce);
        }
    }

    /**
     * @return the propPassword
     */
    public String getPropPassword() {
        return propPassword;
    }

    public static HttpString getXForwardedHeaderName(String suffix) {
        return HttpString.tryFromString("X-Forwarded-".concat(suffix));
    }

    public static HttpString getXForwardedAccountIdHeaderName() {
        return getXForwardedHeaderName("Account-Id");
    }

    public static HttpString getXForwardedRolesHeaderName() {
        return getXForwardedHeaderName("Account-Roles");
    }

    public boolean checkUserCollection() {
        if (this.mclient == null) {
            LOGGER.error("Cannot find account: mongo service is not enabled.");
            return false;
        }

        try {
            var mu = new MongoUtils(this.mclient);

            if (!mu.doesDbExist(this.usersDb)) {
                mu.createDb(this.usersDb);
            }

            if (!mu.doesCollectionExist(this.usersDb, this.usersCollection)) {
                mu.createCollection(this.usersDb, this.usersCollection);
            }
        } catch (Throwable t) {
            LOGGER.error("Error creating users collection", t);
            return false;
        }
        
        return true;
    }

    public long countAccounts() {
        try {
            return mclient.getDatabase(this.getUsersDb())
                    .getCollection(this.getUsersCollection())
                    .estimatedDocumentCount();
        } catch (Throwable t) {
            LOGGER.error("Error counting accounts", t);
            return 1;
        }
    }

    public void createDefaultAccount() {
        if (this.mclient == null) {
            LOGGER.error("Cannot find account: mongo service is not enabled.");
            return;
        }

        if (this.createUserDocument != null) {
            try {
                mclient.getDatabase(this.getUsersDb())
                        .getCollection(this.getUsersCollection(), BsonDocument.class)
                        .insertOne(this.createUserDocument);
            } catch (Throwable t) {
                LOGGER.error("Error creating default account", t);
            }
        }
    }

    public PwdCredentialAccount findAccount(String accountId) {
        var coll = mclient
                .getDatabase(this.getUsersDb())
                .getCollection(this.getUsersCollection());

        Document _account;

        try {
            _account = coll.find(eq(propId, accountId)).first();
        } catch (Throwable t) {
            LOGGER.error("Error finding account {}", propId, t);
            return null;
        }

        if (_account == null) {
            return null;
        }

        JsonElement account;

        try {
            account = JsonPath.read(_account.toJson(), "$");
        } catch (IllegalArgumentException | PathNotFoundException pnfe) {
            LOGGER.warn("Cannot find account {}", accountId);
            return null;
        }

        if (!account.isJsonObject()) {
            LOGGER.warn("Retrived document for account {} is not an object", accountId);
            return null;
        }

        JsonElement _password;

        try {
            _password = JsonPath.read(account, "$.".concat(this.propPassword));
        } catch (PathNotFoundException pnfe) {
            LOGGER.warn("Cannot find pwd property '{}' for account {}", this.propPassword,
                    accountId);
            return null;
        }

        if (!_password.isJsonPrimitive()
                || !_password.getAsJsonPrimitive().isString()) {
            LOGGER.warn("Pwd property of account {} is not a string", accountId);
            return null;
        }

        JsonElement _roles;

        try {
            _roles = JsonPath.read(account, this.jsonPathRoles);
        } catch (PathNotFoundException pnfe) {
            LOGGER.warn("Account with id: {} does not have roles");
            _roles = new JsonArray();
        }

        if (!_roles.isJsonArray()) {
            LOGGER.warn("Roles property of account {} is not an array", accountId);
            return null;
        }

        var roles = new LinkedHashSet<String>();

        _roles.getAsJsonArray().forEach(role -> {
            if (role != null && role.isJsonPrimitive()
                    && role.getAsJsonPrimitive().isString()) {
                roles.add(role.getAsJsonPrimitive().getAsString());
            } else {
                LOGGER.warn("A role of account {} is not a string", accountId);
            }
        });

        return new PwdCredentialAccount(accountId,
                _password.getAsJsonPrimitive().getAsString().toCharArray(),
                roles);
    }

    /**
     * @return the usersDb
     */
    public String getUsersDb() {
        return usersDb;
    }

    /**
     * @param usersDb the usersDb to set
     */
    public void setUsersDb(String usersDb) {
        this.usersDb = usersDb;
    }

    /**
     * @return the usersCollection
     */
    public String getUsersCollection() {
        return usersCollection;
    }
}
