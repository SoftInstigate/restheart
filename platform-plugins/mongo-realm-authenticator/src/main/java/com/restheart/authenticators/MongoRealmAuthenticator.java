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
package com.restheart.authenticators;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mongodb.MongoClient;
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
import java.util.Map;
import java.util.Optional;
import org.bson.BsonDocument;
import org.mindrot.jbcrypt.BCrypt;
import org.restheart.Configuration;
import org.restheart.ConfigurationException;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.plugins.security.PwdCredentialAccount;
import org.restheart.utils.URLUtils;
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

    private String usersUri;
    private String propId = "_id";
    String propPassword = "password";
    private String jsonPathRoles = "$.roles";
    private Boolean bcryptHashedPassword = false;
    Integer bcryptComplexity = 12;
    private Boolean createUser = false;
    private String createUserDocument = null;
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

    private Map<String, Object> ownArgs;
    private PluginsRegistry registry;
    private MongoDelegate delegate;

    private enum MODE {
        DIRECT, PROXY
    };

    private MODE mode;

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void setConf(Map<String, Object> args) {
        var conf = new Configuration(args, true);

        this.ownArgs = conf.getAuthenticators().get("mongoRealmAuthenticator");

        this.cacheEnabled = argValue(ownArgs, "cache-enabled");
        this.cacheSize = argValue(ownArgs, "cache-size");
        this.cacheTTL = argValue(ownArgs, "cache-ttl");

        String _cacheExpirePolicy = argValue(ownArgs, "cache-expire-policy");
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

        this.bcryptHashedPassword = argValue(ownArgs, "bcrypt-hashed-password");

        this.bcryptComplexity = argValue(ownArgs, "bcrypt-complexity");

        this.createUser = argValue(ownArgs, "create-user");
        this.createUserDocument = argValue(ownArgs, "create-user-document");

        // check createUserDocument
        try {
            JsonParser.parseString(this.createUserDocument);
        } catch (JsonParseException ex) {
            throw new ConfigurationException(
                    "wrong configuration file format. "
                    + "create-user-document must be a json document", ex);
        }

        this.propId = argValue(ownArgs, "prop-id");

        if (this.propId.startsWith("$")) {
            throw new ConfigurationException("prop-id must be "
                    + "a root property name not a json path expression. "
                    + "It can use the dot notation.");
        }

        this.propPassword = argValue(ownArgs, "prop-password");

        if (this.propPassword.contains(".")) {
            throw new ConfigurationException("prop-password must be "
                    + "a root level property and cannot contain the char '.'");
        }

        this.jsonPathRoles = argValue(ownArgs, "json-path-roles");

        try {
            this.mode = MODE.valueOf(argValue(ownArgs, "mode"));
        } catch (Throwable t) {
            LOGGER.warn("Wrong mode, either 'direct' or 'proxy', assuming it as 'direct'");
            this.mode = MODE.DIRECT;
        }

        this.usersUri = URLUtils.removeTrailingSlashes(
                argValue(ownArgs, "users-collection-uri"));

        if (mode == MODE.PROXY) {
            var restheartBaseUrl = conf.getRestheartBaseUrl();

            var dbUrl = restheartBaseUrl
                    .resolve(restheartBaseUrl.getPath()
                            .concat(usersUri.substring(0, usersUri.lastIndexOf("/"))));

            var collUrl = restheartBaseUrl.resolve(restheartBaseUrl.getPath()
                    .concat(usersUri));

            this.delegate = new ProxyMongoDelegate(restheartBaseUrl,
                    dbUrl,
                    collUrl,
                    createUserDocument,
                    propId,
                    propPassword,
                    jsonPathRoles);

            if (this.cacheEnabled) {
                this.USERS_CACHE = CacheFactory.createLocalLoadingCache(
                        this.cacheSize,
                        this.cacheExpirePolicy,
                        this.cacheTTL, (String key) -> {
                            return delegate.findAccount(accountIdTrasformer(key));
                        });
            }

            if (!delegate.checkUserCollection()) {
                LOGGER.error("Users collection does not exist and could not be created");
            } else if (this.createUser) {
                LOGGER.trace("Create user option enabled");

                if (delegate.countAccounts() < 1) {
                    this.delegate.createDefaultAccount();
                    LOGGER.info("No user found. Created default user with _id {}",
                            JsonParser.parseString(this.createUserDocument)
                                    .getAsJsonObject().get(this.propId));
                } else {
                    LOGGER.trace("Not creating default user since users exist");
                }
            }
        }
    }

    @InjectMongoClient
    public void setMongoClient(MongoClient mclient) {
        if (mode == MODE.DIRECT) {
            String usersDb = argValue(ownArgs, "users-db");
            String usersColl = argValue(ownArgs, "users-collection");

            this.delegate = new DirectMongoDelegate(mclient,
                    usersDb,
                    usersColl,
                    propId, propPassword,
                    jsonPathRoles,
                    BsonDocument.parse(createUserDocument));

            if (this.cacheEnabled) {
                this.USERS_CACHE = CacheFactory.createLocalLoadingCache(
                        this.cacheSize,
                        this.cacheExpirePolicy,
                        this.cacheTTL, (String key) -> {
                            return delegate.findAccount(accountIdTrasformer(key));
                        });
            }

            if (!delegate.checkUserCollection()) {
                LOGGER.error("Users collection does not exist and could not be created");
            } else if (this.createUser) {
                LOGGER.trace("Create user option enabled");

                if (delegate.countAccounts() < 1) {
                    this.delegate.createDefaultAccount();
                    LOGGER.info("No user found. Created default user with _id {}",
                            JsonParser.parseString(this.createUserDocument)
                                    .getAsJsonObject().get(this.propId));
                } else {
                    LOGGER.trace("Not creating default user since users exist");
                }
            }
        } else {
            LOGGER.warn("Service mongo is enabled, mode should be 'direct' but is {}", mode);
        }
    }

    @InjectPluginsRegistry
    public void setRegistry(PluginsRegistry registry) {
        this.registry = registry;

        // add a global security predicate to deny requests
        // to users collection, containing a filter on password property
        // this avoids clients to potentially steal passwords
        registry.getGlobalSecurityPredicates()
                .add(new DenyFilterOnUserPwdPredicate(this.usersUri,
                        this.propPassword));
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
     * @return the usersUri
     */
    public String getUsersUri() {
        return usersUri;
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
        if (USERS_CACHE == null) {
            return this.delegate.findAccount(this.accountIdTrasformer(id));
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
}
