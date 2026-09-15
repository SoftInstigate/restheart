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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.security.tokens.JwtTokenManager;
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
 * <h2>The database is chosen per request</h2>
 *
 * <p>The {@value #OVERRIDE_KEYS_DB} attached parameter, set by an interceptor at
 * {@code REQUEST_BEFORE_AUTH}, names the database to look the key up in, as
 * {@code override-users-db} does for {@code mongoRealmAuthenticator}. A key is
 * then found only in its own tenant's database, and works only there.
 *
 * <p>There is no fallback to {@code override-users-db}: keys living apart from
 * users is a supported layout, and a fallback would silently move them. Nor is
 * there an override for the collection, for the reason
 * {@code mongoRealmAuthenticator} has none for {@code users-collection}: the
 * collection is part of the schema, the database is the tenant.
 *
 * <h2>Attached properties, as for a password login</h2>
 * <p>{@code attached-props} names request parameters to copy onto the account,
 * exactly as {@code mongoRealmAuthenticator} does. A deployment that attaches
 * per-request facts before authentication — a multi-tenant one attaches the
 * node the request arrived on — needs them on every account it authenticates,
 * whatever the credential: they are what its guards compare, and what a token
 * minted from the account has to carry. Left off, an account from a key would
 * be the one kind that lacks them.
 * <p>They are copied onto a copy. The account built from the key document is
 * what the cache holds, and a property that came from one request must not be
 * found on the account by the next.
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "mongoApiKeyAuthenticator",
        description = "authenticates API keys stored in a MongoDB collection",
        enabledByDefault = false)
public class MongoApiKeyAuthenticator implements Authenticator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoApiKeyAuthenticator.class);

    private static final String SHA_256 = "SHA-256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** The attached parameter naming the database to look a key up in. */
    public static final String OVERRIDE_KEYS_DB = "override-keys-db";

    /**
     * What a verification is cached under. The hash alone is not enough: the
     * same key checked against two databases is two different questions, and
     * answering the second from the first would make the override cosmetic.
     */
    record KeyRef(String db, String hash) {
    }

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
    private List<String> attachedProps = null;

    private LoadingCache<KeyRef, MongoRealmAccount> keysCache = null;

    @OnInit
    public void init() throws ConfigurationException {
        this.keysDb = argOrDefault(config, "keys-db", "restheart");
        this.keysCollection = argOrDefault(config, "keys-collection", "apiKeys");
        this.propHash = argOrDefault(config, "prop-hash", "hash");
        this.propPrincipal = argOrDefault(config, "prop-principal", "user");
        this.propRoles = argOrDefault(config, "prop-roles", "roles");
        this.propExpires = argOrDefault(config, "prop-expires", "expiresAt");
        this.trackLastUsed = argOrDefault(config, "track-last-used", true);
        this.attachedProps = argOrDefault(config, "attached-props", null);

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

    /**
     * Verifies the key against the configured {@code keys-db}, ignoring any
     * per-request override. Use {@link #verify(Request, Credential)} where the
     * request is at hand.
     */
    @Override
    public Account verify(final Credential credential) {
        return verifyIn(this.keysDb, credential);
    }

    /**
     * Verifies the key against the database the request resolves to: the
     * {@value #OVERRIDE_KEYS_DB} attached parameter when present, the
     * configured {@code keys-db} otherwise.
     */
    public Account verify(final Request<?> req, final Credential credential) {
        final var account = verifyIn(getKeysDb(req), credential);

        return account instanceof MongoRealmAccount mra ? withAttachedParams(req, mra) : account;
    }

    /**
     * The account with the configured {@code attached-props} copied from the
     * request, or the account itself when there is nothing to copy.
     *
     * <p>A new account, not the given one: the given one may be the cache's,
     * shared by every request that presents the same key.
     */
    // package-private so the rule can be pinned by a test
    MongoRealmAccount withAttachedParams(final Request<?> req, final MongoRealmAccount account) {
        if (this.attachedProps == null || this.attachedProps.isEmpty() || req == null || account.properties() == null) {
            return account;
        }

        final var attached = req.attachedParams();

        if (attached == null) {
            return account;
        }

        final var props = account.properties().clone();

        for (final var name : this.attachedProps) {
            final var value = attached.get(name);

            if (value == null) {
                continue;
            }

            props.put(name, value instanceof BsonValue bv ? bv : new BsonString(value.toString()));
        }

        return new MongoRealmAccount(account.db(), account.getPrincipal().getName(), new char[0], account.getRoles(), props);
    }

    /**
     * The database a key is looked up in for this request.
     *
     * <p>An override that is present but unusable — blank, or not a string —
     * yields {@code null}, and the key is refused. It is never read as absent:
     * that would look the key up in the configured {@code keys-db}, and a
     * resolver that failed to name a tenant would silently accept the keys of
     * the default one.
     *
     * @param req the request
     * @return the keys database, taking into account the {@value #OVERRIDE_KEYS_DB}
     *         attached parameter; {@code null} when the override is unusable
     */
    public String getKeysDb(final Request<?> req) {
        final Object override = req == null ? null : req.attachedParam(OVERRIDE_KEYS_DB);

        if (override == null) {
            return this.keysDb;
        }

        return override instanceof final String db && !db.isBlank() ? db : null;
    }

    private Account verifyIn(final String db, final Credential credential) {
        if (!(credential instanceof final ApiKeyCredential apiKey)) {
            return null;
        }

        if (db == null) {
            LOGGER.warn("Refusing API key: {} is set but names no usable database", OVERRIDE_KEYS_DB);
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

        final var ref = new KeyRef(db, hash);

        final var account = this.keysCache == null
                ? findKey(ref)
                : this.keysCache.getLoading(ref).orElse(null);

        if (account == null) {
            LOGGER.debug("API key not found in {}.{}", db, this.keysCollection);
            return null;
        }

        if (this.trackLastUsed) {
            touch(ref);
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
    private MongoRealmAccount findKey(final KeyRef ref) {
        final BsonDocument key;

        // getDatabase() inside the try: it throws for a name MongoDB rejects,
        // and a per-request override can carry one. That is a refusal, not a 500.
        try {
            key = mclient.getDatabase(ref.db())
                    .getCollection(this.keysCollection)
                    .withDocumentClass(BsonDocument.class)
                    .find(eq(this.propHash, ref.hash()))
                    .first();
        } catch (final Throwable t) {
            LOGGER.error("Error finding API key in {}.{}", ref.db(), this.keysCollection, t);
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

        return accountOf(ref.db(), key);
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
     *
     * <h3>And the database the key was found in, as {@code authDb}</h3>
     *
     * <p>That is what tells a tenant's key from another's once it has become a
     * JWT: the token carries {@code authDb} as one from a password login does,
     * so a deployment can check it against the tenant the request is for, and
     * the token cache does not hand one tenant's token to another. It is safe
     * on renewal because the {@code apiKey} marker stops the account being
     * re-read from the users store before {@code authDb} is ever looked at.
     */
    MongoRealmAccount accountOf(final String db, final BsonDocument key) {
        final var principal = key.get(this.propPrincipal);

        if (principal == null || !principal.isString() || principal.asString().getValue().isBlank()) {
            LOGGER.warn("API key document has no usable '{}' property", this.propPrincipal);
            return null;
        }

        final var name = principal.asString().getValue();

        // The apiKey marker travels with the account so that a token issued to it can be told
        // apart later. Renewal re-reads the account from the users store, which would replace the
        // roles this key was deliberately given with the user's own — widening a credential whose
        // whole point is being narrower than its owner. The marker is the rule: authDb, which the
        // account now carries too, says which tenant the key belongs to, not what may be re-read.
        return new MongoRealmAccount(db,
                name,
                new char[0],
                rolesOf(key),
                new BsonDocument("_id", new BsonString(name))
                        .append("authDb", new BsonString(db))
                        .append(JwtTokenManager.FROM_API_KEY, BsonBoolean.TRUE));
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
    private void touch(final KeyRef ref) {
        try {
            mclient.getDatabase(ref.db())
                    .getCollection(this.keysCollection)
                    .withDocumentClass(BsonDocument.class)
                    .updateOne(eq(this.propHash, ref.hash()), currentDate("lastUsedAt"));
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

            for (int i = 0;i < digest.length;i++) {
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
