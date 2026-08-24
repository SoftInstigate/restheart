/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.authenticators;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.currentDate;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.security.ApiKeyCredential;
import org.restheart.security.MongoRealmAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;

/**
 * Verifies an {@link ApiKeyCredential} against a MongoDB collection.
 *
 * <h2>Why this is not {@code mongoRealmAuthenticator} with another property</h2>
 *
 * <p>An API key is a different kind of credential from a password, and most of
 * what follows falls out of that: it is high-entropy rather than memorable, it
 * is looked up <em>by itself</em> rather than by a principal, it expires, it is
 * revoked one at a time, and it grants less than the user who created it.
 *
 * <h2>SHA-256, not bcrypt</h2>
 *
 * <p>Copying {@code mongoRealmAuthenticator}'s bcrypt would be the obvious
 * thing and the wrong one. bcrypt is deliberately slow, and that slowness is
 * the defence for a low-entropy secret a human chose. A key is 32 random bytes:
 * brute force is not the threat, and the cost is real — bcrypt at complexity 12
 * is tens of milliseconds, paid on <em>every request</em> rather than once per
 * login.
 *
 * <h2>Roles come from the key, never from the user</h2>
 *
 * <p>The account built here carries the roles named on the key document. That
 * makes a key deny-by-default: an ACL written for {@code user} does not match a
 * key whose role is something else, so a key reaches only what was granted to
 * it deliberately. Getting it wrong yields a key that cannot do enough — not
 * one that can do too much.
 *
 * <p>A key document naming no roles produces an account with no roles. It must
 * never fall back to the user's.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "mongoApiKeyAuthenticator",
        description = "authenticates API keys stored in a MongoDB collection",
        enabledByDefault = false)
public class MongoApiKeyAuthenticator implements Authenticator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoApiKeyAuthenticator.class);

    private static final String SHA_256 = "SHA-256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    @Inject("config")
    private Map<String, Object> config;

    @Inject("mclient")
    private MongoClient mclient;

    private String keysDb;
    private String keysCollection;
    private String propHash;
    private String propPrincipal;
    private String propRoles;
    private String propExpires;
    private boolean trackLastUsed;

    private LoadingCache<String, MongoRealmAccount> keysCache = null;

    @OnInit
    public void init() throws ConfigurationException {
        this.keysDb = argOrDefault(config, "keys-db", "restheart");
        this.keysCollection = argOrDefault(config, "keys-collection", "apiKeys");
        this.propHash = argOrDefault(config, "prop-hash", "hash");
        this.propPrincipal = argOrDefault(config, "prop-principal", "user");
        this.propRoles = argOrDefault(config, "prop-roles", "roles");
        this.propExpires = argOrDefault(config, "prop-expires", "expiresAt");
        this.trackLastUsed = argOrDefault(config, "track-last-used", true);

        final boolean cacheEnabled = argOrDefault(config, "cache-enabled", true);

        if (cacheEnabled) {
            final int cacheSize = argOrDefault(config, "cache-size", 1000);
            final int cacheTTL = argOrDefault(config, "cache-ttl", 60_000);
            final String expirePolicy = argOrDefault(config, "cache-expire-policy", "AFTER_WRITE");

            final Cache.EXPIRE_POLICY policy;
            try {
                policy = Cache.EXPIRE_POLICY.valueOf(expirePolicy);
            } catch (final IllegalArgumentException iae) {
                throw new ConfigurationException("wrong configuration of mongoApiKeyAuthenticator, "
                    + "cache-expire-policy is not valid: " + expirePolicy);
            }

            // Revocation takes effect within this TTL. Revocation immediacy is
            // most of what a key offers over a password, so a long cache
            // quietly gives away the feature — hence the short default.
            this.keysCache = CacheFactory.createLocalLoadingCache(cacheSize, policy, cacheTTL, this::findKey);
        }
    }

    @Override
    public Account verify(final Credential credential) {
        if (!(credential instanceof final ApiKeyCredential apiKey)) {
            return null;
        }

        if (this.mclient == null) {
            LOGGER.error("Cannot verify API key: mongo service is not enabled.");
            return null;
        }

        final var hash = sha256(apiKey.getKey());

        if (hash == null) {
            return null;
        }

        final var account = this.keysCache == null
            ? findKey(hash)
            : this.keysCache.getLoading(hash).orElse(null);

        if (account == null) {
            LOGGER.debug("API key not found");
            return null;
        }

        if (this.trackLastUsed) {
            touch(hash);
        }

        return account;
    }

    /**
     * Not supported: an API key is self-identifying, so there is no principal to
     * verify it against. Answering anything else here would let a caller pass a
     * key as somebody else's credential.
     */
    @Override
    public Account verify(final String id, final Credential credential) {
        return null;
    }

    @Override
    public Account verify(final Account account) {
        return account;
    }

    /**
     * Loads the key document and builds the account, or {@code null} when the
     * key is unknown or spent.
     */
    private MongoRealmAccount findKey(final String hash) {
        final var coll = mclient.getDatabase(this.keysDb)
            .getCollection(this.keysCollection)
            .withDocumentClass(BsonDocument.class);

        final BsonDocument key;

        try {
            key = coll.find(eq(this.propHash, hash)).first();
        } catch (final Throwable t) {
            LOGGER.error("Error finding API key in {}.{}", this.keysDb, this.keysCollection, t);
            return null;
        }

        if (key == null) {
            return null;
        }

        // Checked here as well as by the TTL index: a TTL index reclaims lazily,
        // on a background sweep, so a just-expired key can still be present.
        if (isExpired(key)) {
            LOGGER.debug("API key is expired");
            return null;
        }

        return accountOf(key);
    }

    /**
     * Builds the account for a key document, or {@code null} when the document
     * cannot name a principal.
     *
     * <h3>The properties carry the principal, and that is not decoration</h3>
     *
     * <p>An account's identity is read from its <em>properties</em>, not from
     * its principal name, by everything downstream that asks who is calling: an
     * ACL predicate writing {@code equals(@user._id, ${userId})}, a GraphQL
     * mapping matching {@code $arg: "@user._id"}, an aggregation interpolating
     * {@code @user}. All of them resolve against
     * {@link org.restheart.security.WithProperties#propertiesAsMap()}.
     *
     * <p>An empty document therefore does not fail — it resolves to
     * {@code null}, and a query matching on {@code null} matches nothing. The
     * caller authenticates, is authorised, gets a {@code 200}, and sees no data.
     * That is the worst shape a bug can take here, and it is why this is a
     * document rather than an empty one.
     *
     * <h3>Why only the principal, and not the key document</h3>
     *
     * <p>The key document holds the hash. It is also the tenant's to shape, so
     * whatever else sits in it was not written with an ACL predicate or a
     * GraphQL context in mind. Identity is the one thing every consumer needs
     * and the one thing that is safe to hand over; anything more is a decision
     * to be taken deliberately, with a configuration option to go with it.
     *
     * <p>The key is fixed at {@code _id} rather than following
     * {@code prop-principal}, because {@code _id} is what a consumer writes.
     * {@code prop-principal} says where to read the principal <em>from</em> in
     * this collection, which is a different question from what to call it once
     * it is on the account.
     */
    MongoRealmAccount accountOf(final BsonDocument key) {
        final var principal = key.get(this.propPrincipal);

        if (principal == null || !principal.isString() || principal.asString().getValue().isBlank()) {
            LOGGER.warn("API key document has no usable '{}' property", this.propPrincipal);
            return null;
        }

        final var name = principal.asString().getValue();

        return new MongoRealmAccount(this.keysDb,
            name,
            new char[0],
            rolesOf(key),
            new BsonDocument("_id", new BsonString(name)));
    }

    /**
     * The roles named on the key document — never the user's. An absent or
     * malformed property yields no roles, which is the safe reading.
     */
    // package-private so the rule below can be pinned by a test
    Set<String> rolesOf(final BsonDocument key) {
        final var roles = new LinkedHashSet<String>();
        final var value = key.get(this.propRoles);

        if (value != null && value.isArray()) {
            for (final BsonValue role : value.asArray()) {
                if (role.isString()) {
                    roles.add(role.asString().getValue());
                }
            }
        }

        if (roles.isEmpty()) {
            LOGGER.warn("API key for '{}' names no roles; it will be able to reach only what is "
                + "granted to no role at all", key.get(this.propPrincipal));
        }

        return roles;
    }

    boolean isExpired(final BsonDocument key) {
        final var expires = key.get(this.propExpires);

        if (expires == null || expires.isNull()) {
            return false;
        }

        if (expires.isDateTime()) {
            return expires.asDateTime().getValue() <= System.currentTimeMillis();
        }

        LOGGER.warn("API key '{}' property is not a date; treating the key as expired", this.propExpires);
        return true;
    }

    /**
     * Records that the key was used.
     *
     * <p>Deliberately fire-and-forget: this is bookkeeping, and a failure to
     * write it must never turn a valid key into a rejected one.
     */
    private void touch(final String hash) {
        try {
            mclient.getDatabase(this.keysDb)
                .getCollection(this.keysCollection)
                .withDocumentClass(BsonDocument.class)
                .updateOne(eq(this.propHash, hash), currentDate("lastUsedAt"));
        } catch (final Throwable t) {
            LOGGER.warn("Could not update lastUsedAt for an API key", t);
        }
    }

    /**
     * Hex-encoded SHA-256 of the key.
     *
     * <p>Encoded through a {@link ByteBuffer} rather than {@code new String(key)}
     * so the secret never becomes a {@code String} — one would sit in the heap
     * until it happened to be collected, which is the reason the credential
     * holds a {@code char[]} in the first place.
     */
    static String sha256(final char[] key) {
        final var encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(key));
        final var bytes = new byte[encoded.remaining()];
        encoded.get(bytes);

        try {
            final var digest = MessageDigest.getInstance(SHA_256).digest(bytes);
            final var hex = new char[digest.length * 2];

            for (int i = 0; i < digest.length; i++) {
                hex[i * 2] = HEX[(digest[i] >> 4) & 0xf];
                hex[i * 2 + 1] = HEX[digest[i] & 0xf];
            }

            return new String(hex);
        } catch (final NoSuchAlgorithmException nsae) {
            LOGGER.error("{} is not available", SHA_256, nsae);
            return null;
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (encoded.hasArray()) {
                Arrays.fill(encoded.array(), (byte) 0);
            }
        }
    }
}
