/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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
package org.restheart.security.authenticators;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.mindrot.jbcrypt.BCrypt;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;
import static org.restheart.mongodb.ConnectionChecker.connected;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.security.MongoRealmAccount;
import org.restheart.security.PwdCredentialAccount;
import org.restheart.security.utils.MongoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mongodb.client.MongoClient;
import static com.mongodb.client.model.Filters.eq;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.util.HexConverter;
import io.undertow.util.HttpString;
import static io.undertow.util.RedirectBuilder.UTF_8;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "mongoRealmAuthenticator", description = "authenticate requests against client credentials stored in mongodb")
public class MongoRealmAuthenticator implements Authenticator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoRealmAuthenticator.class);

    private String propId = "_id";
    private String usersDb;
    private String usersCollection;
    private String propPassword = "password";
    private String jsonPathRoles = "$.roles";
    private boolean bcryptHashedPassword = false;
    private int bcryptComplexity = 12;
    private boolean enforceMinimumPasswordStrength = false;
    private int minimumPasswordStrength = 3;
    private BsonDocument createUserDocument = null;
    private Cache.EXPIRE_POLICY cacheExpirePolicy = Cache.EXPIRE_POLICY.AFTER_WRITE;
    private List<String> attachedProps = null;
    private record CacheKey(String id, String db) {};
    private LoadingCache<CacheKey, MongoRealmAccount> USERS_CACHE = null;
    private static final transient Cache<CacheKey, String> USERS_PWDS_CACHE = CacheFactory.createLocalCache(1_000, Cache.EXPIRE_POLICY.AFTER_READ, 20 * 60 * 1_000);

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("config")
    Map<String, Object> config;

    @Inject("mclient")
    private MongoClient mclient;

    @OnInit
    public void init() {
        this.usersDb = argOrDefault(config, "users-db", "restheart");
        this.usersCollection = argOrDefault(config, "users-collection", "users");

        final String _cacheExpirePolicy = arg(config, "cache-expire-policy");
        if (_cacheExpirePolicy != null) {
            try {
                this.cacheExpirePolicy = Cache.EXPIRE_POLICY.valueOf((String) _cacheExpirePolicy);
            } catch (final IllegalArgumentException iae) {
                throw new ConfigurationException("wrong configuration file format. cache-expire-policy valid values are ".concat(Arrays.toString(Cache.EXPIRE_POLICY.values())));
            }
        }

        this.enforceMinimumPasswordStrength= argOrDefault(config, "enforce-minimum-password-strength", false);
        this.minimumPasswordStrength = argOrDefault(config, "minimum-password-strength", 3);

        this.bcryptHashedPassword = arg(config, "bcrypt-hashed-password");
        this.bcryptComplexity = arg(config, "bcrypt-complexity");

        final boolean createUser = arg(config, "create-user");
        final String _createUserDocument = arg(config, "create-user-document");

        // check createUserDocument
        try {
            this.createUserDocument = BsonDocument.parse(_createUserDocument);
        } catch (final JsonParseException ex) {
            throw new ConfigurationException("wrong configuration file format. create-user-document must be a json document", ex);
        }

        this.propId = arg(config, "prop-id");

        if (this.propId.startsWith("$")) {
            throw new ConfigurationException(
                    "prop-id must be a root property name not a json path expression. It can use the dot notation.");
        }

        this.propPassword = arg(config, "prop-password");

        if (this.propPassword.contains(".")) {
            throw new ConfigurationException(
                    "prop-password must be a root level property and cannot contain the char '.'");
        }

        this.jsonPathRoles = arg(config, "json-path-roles");

        this.attachedProps = argOrDefault(config, "attached-props", null);

        final boolean cacheEnabled = arg(config, "cache-enabled");
        if (cacheEnabled) {
            final int cacheSize = arg(config, "cache-size");
            final int cacheTTL = arg(config, "cache-ttl");
            this.USERS_CACHE = CacheFactory.createLocalLoadingCache(cacheSize, this.cacheExpirePolicy, cacheTTL, key -> findAccount(key));
        }

        try {
            if (!checkUserCollection()) {
                LOGGER.error("Users collection does not exist and could not be created");
            } else if (createUser) {
                LOGGER.trace("Create user option enabled");

                if (countAccounts() < 1) {
                    createDefaultAccount();
                    LOGGER.info("No user found. Created default user with _id {}", this.createUserDocument.get(this.propId));
                } else {
                    LOGGER.trace("Not creating default user since users exist");
                }
            }
        } catch (final IllegalStateException ise) {
            LOGGER.error(ise.getMessage());
        }
    }

    @Override
    public Account verify(final Account account) {
        return account;
    }

    public Account verify(final Request<?> req, final String id, final Credential credential) {
        var usersDb = getUsersDb(req);
        LOGGER.debug("MongoRealmAuthenticator.verify: username={} usersDb={} override-users-db={}", id, usersDb, req.attachedParam("override-users-db"));
        var account = verify(usersDb, id, credential);
        
        // Copy configured attached parameters to account properties
        if (account instanceof MongoRealmAccount mongoAccount && this.attachedProps != null && !this.attachedProps.isEmpty()) {
            copyAttachedParamsToAccount(req, mongoAccount);
        }
        
        return account;
    }

    private Account verify(final String usersDb, final String id, final Credential credential) {
        var verificationStartTime = System.currentTimeMillis();
        
        if (credential == null) {
            LOGGER.debug("Cannot verify null credential for user '{}'", id);
            return null;
        }

        var accountLookupStartTime = System.currentTimeMillis();
        final var ref = getAccount(usersDb, id);
        var accountLookupDuration = System.currentTimeMillis() - accountLookupStartTime;
        
        if (ref == null) {
            var totalDuration = System.currentTimeMillis() - verificationStartTime;
            LOGGER.debug("User '{}' not found in {}.{} ({}ms)", id, usersDb, this.usersCollection, totalDuration);
            return null;
        }

        boolean verified = false;
        var credentialVerificationStartTime = System.currentTimeMillis();

        if (credential instanceof final PasswordCredential passwordCredential) {
            verified = verifyPasswordCredential(usersDb, ref, passwordCredential);
        } else if (credential instanceof final DigestCredential digestCredential) {
            verified = verifyDigestCredential(ref, digestCredential);
        } else {
            LOGGER.warn("MongoRealmAuthenticator does not support credential of type {} for user '{}'", 
                credential.getClass().getSimpleName(), id);
        }
        
        var credentialVerificationDuration = System.currentTimeMillis() - credentialVerificationStartTime;
        var totalDuration = System.currentTimeMillis() - verificationStartTime;

        if (verified) {
            LOGGER.debug("│  │  └─ User '{}' verified - Lookup: {}ms, Credential: {}ms, Total: {}ms",
                id, accountLookupDuration, credentialVerificationDuration, totalDuration);

            updateAuthTokenCache(ref);
            return ref;
        } else {
            LOGGER.debug("│  │  └─ User '{}' verification failed ({}ms)", id, totalDuration);
            return null;
        }
    }

    @Override
    public Account verify(final String id, final Credential credential) {
        return verify(this.usersDb, id, credential);
    }

    @Override
    public Account verify(final Credential credential) {
        return null;
    }

    /**
     * Copies configured request parameters to account properties.
     * Only parameters listed in the attached-props configuration are copied.
     * Missing parameters are silently skipped without errors.
     * 
     * @param req the request containing attached parameters
     * @param account the MongoRealmAccount to update with parameters
     */
    private void copyAttachedParamsToAccount(final Request<?> req, final MongoRealmAccount account) {
        if (account.properties() == null) {
            LOGGER.debug("Cannot copy attached params: account properties is null");
            return;
        }
        
        for (String paramName : this.attachedProps) {
            Object paramValue = req.attachedParam(paramName);
            
            if (paramValue != null) {
                // Convert the parameter value to a BsonValue and add to account properties
                try {
                    if (paramValue instanceof String) {
                        account.properties().put(paramName, new BsonString((String) paramValue));
                    } else if (paramValue instanceof BsonDocument) {
                        account.properties().put(paramName, (BsonDocument) paramValue);
                    } else if (paramValue instanceof org.bson.BsonValue) {
                        account.properties().put(paramName, (org.bson.BsonValue) paramValue);
                    } else {
                        // For other types, convert to string
                        account.properties().put(paramName, new BsonString(paramValue.toString()));
                    }
                    LOGGER.debug("Copied attached param '{}' to account properties for user '{}'", paramName, account.getPrincipal().getName());
                } catch (Exception e) {
                    LOGGER.warn("Failed to copy attached param '{}' to account properties: {}", paramName, e.getMessage());
                }
            } else {
                LOGGER.trace("Attached param '{}' not found in request, skipping", paramName);
            }
        }
    }

    /**
     * @return the bcryptComplexity
     */
    public int getBcryptComplexity() {
        return bcryptComplexity;
    }

    /**
     * @return true if the password must be hashed
     */
    public boolean isBcryptHashedPassword() {
        return bcryptHashedPassword;
    }

    /**
     * Integer from 0 to 4
     * 0 Weak （guesses < 3^10）
     * 1 Fair （guesses < 6^10）
     * 2 Good （guesses < 8^10）
     * 3 Strong （guesses < 10^10）
     * 4 Very strong （guesses >= 10^10）
     *
     * @return the minimumPasswordStrength
     */
    public int getMinimumPasswordStrength() {
        return minimumPasswordStrength;
    }

    /**
     * @return true if the password st be hashed
     */
    public boolean isEnforceMinimumPasswordStrength() {
        return enforceMinimumPasswordStrength;
    }

    /**
     *
     * @param ref
     * @param credential
     * @return true if credential verifies successfully against ref account
     */
    private boolean verifyPasswordCredential(final String usersDb, final PwdCredentialAccount ref, final PasswordCredential credential) {
        if (ref == null
                || ref.getPrincipal() == null
                || ref.getPrincipal().getName() == null
                || ref.getCredentials() == null
                || ref.getCredentials().getPassword() == null
                || credential == null || credential.getPassword() == null) {
            return false;
        }

        return checkPassword(usersDb, ref.getPrincipal().getName(), this.bcryptHashedPassword, credential.getPassword(), ref.getCredentials().getPassword());
    }

    /**
     *
     * @param ref
     * @param credential
     * @return true if password verified successfully
     */
    private boolean verifyDigestCredential(final PwdCredentialAccount ref, final DigestCredential credential) {
        if (this.bcryptHashedPassword) {
            LOGGER.error(
                    "Digest authentication cannot support bcryped stored password, consider using basic authetication over TLS");
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
            final var digest = credential.getAlgorithm().getMessageDigest();

            digest.update(ref.getPrincipal().getName().getBytes(UTF_8));
            digest.update((byte) ':');
            digest.update(credential.getRealm().getBytes(UTF_8));
            digest.update((byte) ':');
            digest.update(new String(ref.getCredentials().getPassword()).getBytes(UTF_8));

            final var ha1 = HexConverter.convertToHexBytes(digest.digest());

            return credential.verifyHA1(ha1);
        } catch (final NoSuchAlgorithmException | UnsupportedEncodingException ne) {
            LOGGER.error(ne.getMessage(), ne);
            return false;
        }
	}

    private boolean checkPassword(final String usersDb, final String username, final boolean hashed, final char[] password, final char[] expected) {
        if (hashed) {
            if (username == null || password == null || expected == null) {
                return false;
            }

            final var _password = new String(password);
            final var _expected = new String(expected);

            // speedup bcrypted pwd check if already checked.
            // bcrypt check is very CPU intensive by design.
            final var cacheKey = new CacheKey(username.concat(_expected), usersDb);
            final var _cachedPwd = USERS_PWDS_CACHE.get(cacheKey);

            if (_cachedPwd != null && _cachedPwd.isPresent() && _cachedPwd.get().equals(_password)) {
                return true;
            }

            try {
                final boolean check = BCrypt.checkpw(_password, _expected);

                if (check) {
                    USERS_PWDS_CACHE.put(cacheKey, _password);
                    return true;
                } else {
                    return false;
                }
            } catch (final Throwable t) {
                USERS_PWDS_CACHE.invalidate(cacheKey);
                LOGGER.warn("Error checking bcryped pwd hash", t);
                return false;
            }
        } else {
            return Arrays.equals(password, expected);
        }
    }

    private MongoRealmAccount getAccount(final String usersDb, final String id) {
        if (this.mclient == null) {
            LOGGER.error("Cannot find account: mongo service is not enabled.");
            return null;
        }

        final var cacheKey = new CacheKey(id, usersDb);

        if (USERS_CACHE == null) {
            return findAccount(cacheKey);
        } else {
            final var _account = USERS_CACHE.getLoading(cacheKey);

            if (_account != null && _account.isPresent()) {
                return _account.get();
            } else {
                return null;
            }
        }
    }

    /**
     * if client authenticates passing the real credentials, update the account
     * in the auth-token cache, otherwise the client authenticating with the
     * auth-token will not see roles updates until the cache expires (by default
     * TTL is 15 minutes after last request)
     *
     * @param account
     */
    private void updateAuthTokenCache(final PwdCredentialAccount account) {
        try {
            final var _tm = registry.getTokenManager();

            if (_tm != null) {
                final var tm = _tm.getInstance();

                if (tm.get(account) != null) {
                    tm.update(account);
                }
            }
        } catch (final ConfigurationException pce) {
            LOGGER.warn("error getting the token manager", pce);
        }
    }

    /**
     * @return the propPassword
     */
    public String getPropPassword() {
        return propPassword;
    }

    public static HttpString getXForwardedHeaderName(final String suffix) {
        return HttpString.tryFromString("X-Forwarded-".concat(suffix));
    }

    public static HttpString getXForwardedAccountIdHeaderName() {
        return getXForwardedHeaderName("Account-Id");
    }

    public static HttpString getXForwardedRolesHeaderName() {
        return getXForwardedHeaderName("Account-Roles");
    }

    public boolean checkUserCollection() throws IllegalStateException {
        if (this.mclient == null) {
            throw new IllegalStateException("Cannot check user collection: mongo service is not enabled.");
        }

        if (!connected(this.mclient)) {
            throw new IllegalStateException("Cannot check user collection: MongoDB not connected.");
        }

        try {
            final var mu = new MongoUtils(this.mclient);

            if (!mu.doesDbExist(getUsersDb())) {
                mu.createDb(getUsersDb());
            }

            if (!mu.doesCollectionExist(getUsersDb(), this.usersCollection)) {
                mu.createCollection(getUsersDb(), this.usersCollection);
            }
        } catch (final Throwable t) {
            LOGGER.error("Error creating users collection", t);
            return false;
        }

        return true;
    }

    private long countAccounts() {
        try {
            return mclient.getDatabase(this.getUsersDb()).getCollection(this.getUsersCollection()).estimatedDocumentCount();
        } catch (final Throwable t) {
            LOGGER.error("Error counting accounts", t);
            return 1;
        }
    }

    private void createDefaultAccount() {
        if (this.mclient == null) {
            LOGGER.error("Cannot find account: mongo service is not enabled.");
            return;
        }

        if (this.createUserDocument != null) {
            try {
                mclient.getDatabase(this.getUsersDb()).getCollection(this.getUsersCollection()).withDocumentClass(BsonDocument.class).insertOne(this.createUserDocument);
            } catch (final Throwable t) {
                LOGGER.error("Error creating default account", t);
            }
        }
    }

    private MongoRealmAccount findAccount(final CacheKey key) {
        final var coll = mclient.getDatabase(key.db()).getCollection(this.getUsersCollection()).withDocumentClass(BsonDocument.class);

        BsonDocument _account;

        try {
            _account = coll.find(eq(propId, key.id())).first();
        } catch (final Throwable t) {
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
            LOGGER.warn("Cannot find account {}", key.id());
            return null;
        }

        if (!account.isJsonObject()) {
            LOGGER.warn("Retrieved document for account {} is not an object", key.id());
            return null;
        }

        JsonElement _password;

        try {
            final var ctx = JsonPath.parse(account);
            _password = ctx.read("$.".concat(this.propPassword));
            account = ctx.delete("$.".concat(this.propPassword)).json();
        } catch (final PathNotFoundException pnfe) {
            LOGGER.warn("Cannot find pwd property '{}' for account {}", this.propPassword, key.id());
            return null;
        }

        if (!_password.isJsonPrimitive() || !_password.getAsJsonPrimitive().isString()) {
            LOGGER.warn("Pwd property of account {} is not a string", key.id());
            return null;
        }

        JsonElement _roles;

        try {
            _roles = JsonPath.read(account, this.jsonPathRoles);
        } catch (final PathNotFoundException pnfe) {
            LOGGER.warn("Account with id: {} does not have roles", key.id());
            _roles = new JsonArray();
        }

        if (!_roles.isJsonArray()) {
            LOGGER.warn("Roles property of account {} is not an array", key.id());
            return null;
        }

        final var roles = new LinkedHashSet<String>();

        _roles.getAsJsonArray().forEach(role -> {
            if (role != null && role.isJsonPrimitive() && role.getAsJsonPrimitive().isString()) {
                roles.add(role.getAsJsonPrimitive().getAsString());
            } else {
                LOGGER.warn("A role of account {} is not a string", key.db());
            }
        });

        final var properties = BsonDocument.parse(account.toString()); // used this because password has been removed from account
        properties.put("authDb", new BsonString(key.db()));

        return new MongoRealmAccount(key.db(), key.id(), _password.getAsJsonPrimitive().getAsString().toCharArray(), roles, properties);
    }

    /**
     * this returns the default users db, does not take into account the overrideUsersDbHeader option
     * @return the usersDb
     */
    public String getUsersDb() {
        return usersDb;
    }

    /**
     * @param req the request
     * @return the usersDb taking into account the override-users-db attached parameter
     */
    public String getUsersDb(final Request<?> req) {
        String overrideUsersDb = req.attachedParam("override-users-db");
        return overrideUsersDb != null ? overrideUsersDb : this.usersDb;
    }

    /**
     * @param usersDb the usersDb to set
     */
    public void setUsersDb(final String usersDb) {
        this.usersDb = usersDb;
    }

    /**
     * @return the usersCollection
     */
    public String getUsersCollection() {
        return usersCollection;
    }

    /**
     * @return the jsonPathRoles
     */
    public String getJsonPathRoles() {
        return jsonPathRoles;
    }
}
