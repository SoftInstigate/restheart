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
package org.restheart.security.tokens;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bson.BsonString;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.configuration.ConfigurationException;
import org.restheart.configuration.Utils;
import org.restheart.exchange.Request;
import org.restheart.logging.RequestPhaseContext;
import org.restheart.logging.RequestPhaseContext.Phase;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.TokenManager;
import org.restheart.security.BaseAccount;
import org.restheart.security.authenticators.MongoRealmAuthenticator;
import org.restheart.security.JwtAccount;
import org.restheart.security.PwdCredentialAccount;
import org.restheart.security.WithProperties;
import org.restheart.utils.Pair;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.Sets;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "jwtTokenManager", description = "issues and verifies auth tokens in a cluster compatible way", enabledByDefault = false)
public class JwtTokenManager implements TokenManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenManager.class);

    private static final String ERROR_UNSUPPORTED_JWT_CLAIM_TYPE = "Cannot add claim {} to jwt because of unsupported type";
    private static final int MAX_CACHE_SIZE = 1000;
    protected static final String ROLES = "roles";

    private LoadingCache<ComparableAccount, Token> jwtCache;
    private JWTVerifier verifier;
    private Algorithm algo;
    private String srvURI = "/tokens";
    private int ttl = 15;
    private String issuer = "restheart.org";
    private String[] audience;
    private boolean enabled = false;
    private List<String> accountPropertiesClaims;
    private volatile JwtIssuer issuerImpl;

    @Inject("config")
    Map<String, Object> config;

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("jwtConfigProvider")
    private JwtConfigProvider.JwtConfig jwtConfig;

    @OnInit
    public void init() throws ConfigurationException {
        this.enabled = true;

        this.srvURI = arg(config, "srv-uri");
        this.ttl = arg(config, "ttl");

        if (ttl < 1) {
            this.enabled = false;
            throw new ConfigurationException("TTL minimum value is 1 minute");
        }

        // Use shared JWT configuration from provider
        if (jwtConfig == null) {
            throw new ConfigurationException("jwtConfigProvider not available. Ensure it is enabled.");
        }

        // Get algorithm from provider config
        String algorithmName = jwtConfig.algorithm();
        try {
            this.algo = getAlgorithm(algorithmName, jwtConfig.key());
        } catch (final Exception e) {
            this.enabled = false;
            throw new ConfigurationException("error setting up JWT algorithm: " + algorithmName, e);
        }

        // Use issuer and audience from provider
        this.issuer = jwtConfig.issuer();
        this.audience = jwtConfig.audience();

        // The claim policy is shared with every other JWT issuer via jwtConfigProvider — see
        // JwtIssuer. The legacy per-plugin setting still wins when set, to not break existing
        // deployments, but it is deprecated exactly like jwt-key was.
        var legacyClaims = this.<List<String>>argOrDefaultNullable("account-properties-claims");

        if (legacyClaims != null) {
            LOGGER.warn("jwtTokenManager/account-properties-claims is deprecated: configure "
                    + "account-properties-claims in jwtConfigProvider instead, so that every JWT "
                    + "issuer applies the same claims. Support for this setting will be removed.");
        }

        this.accountPropertiesClaims = legacyClaims != null
                ? legacyClaims
                : jwtConfig.accountPropertiesClaims();

        jwtCache = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE,
                Cache.EXPIRE_POLICY.AFTER_WRITE,
                ttl * 1000 * 60 - 500, // -500 makes sure that cache entry expires always before token
                key -> newToken(key.wrapped(), key.claims()));

        try {
            this.verifier = jwtConfig.hasAudience()
                    ? JWT.require(algo).withIssuer(issuer).withAudience(audience).build()
                    : JWT.require(algo).withIssuer(issuer).build();

        } catch (final Exception e) {
            this.enabled = false;
            throw new ConfigurationException("error setting the verifier", e);
        }

    }

    /** Reads an optional config value without the "using default" log noise. */
    @SuppressWarnings("unchecked")
    private <T> T argOrDefaultNullable(String key) {
        return (T) argOrDefault(config, key, null);
    }

    /**
     * The shared JWT issuance policy. Built lazily: resolving the password property name needs
     * {@code mongoRealmAuthenticator}, which may not be initialized when this plugin's
     * {@code @OnInit} runs.
     */
    JwtIssuer issuer() {
        var local = this.issuerImpl;

        if (local == null) {
            synchronized (this) {
                local = this.issuerImpl;
                if (local == null) {
                    local = new JwtIssuer(algo, issuer, audience, accountPropertiesClaims, passwordProperty());
                    this.issuerImpl = local;
                }
            }
        }

        return local;
    }

    /**
     * The password property name from {@code mongoRealmAuthenticator/prop-password}, so that the
     * denylist covers it even when the deployment renames it.
     */
    private String passwordProperty() {
        if (registry == null) {
            return JwtIssuer.DEFAULT_PASSWORD_PROPERTY;
        }

        try {
            var pr = registry.getAuthenticator("mongoRealmAuthenticator");
            if (pr != null && pr.isEnabled()
                    && pr.getInstance() instanceof MongoRealmAuthenticator mra) {
                var prop = mra.getPropPassword();
                if (prop != null && !prop.isBlank()) {
                    return prop;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not resolve mongoRealmAuthenticator/prop-password, using default", e);
        }

        return JwtIssuer.DEFAULT_PASSWORD_PROPERTY;
    }

    private Algorithm getAlgorithm(final String name, final String key) {
        if (name == null || key == null) {
            throw new IllegalArgumentException("algorithm and key are required.");
        }

        return switch (name) {
            case "HMAC256", "HS256" -> Algorithm.HMAC256(key.getBytes(StandardCharsets.UTF_8));
            case "HMAC384", "HS384" -> Algorithm.HMAC384(key.getBytes(StandardCharsets.UTF_8));
            case "HMAC512", "HS512" -> Algorithm.HMAC512(key.getBytes(StandardCharsets.UTF_8));
            default -> throw new IllegalArgumentException("unsupported algorithm for JwtTokenManager: " + name + " (only HMAC algorithms supported)");
        };
    }

    @Override
    public Account verify(final Account account) {
        return enabled ? account : null;
    }

    @Override
    public Account verify(final String id, final Credential credential) {
        var verificationStartTime = System.currentTimeMillis();

        if (!enabled) {
            LOGGER.debug("JwtTokenManager is disabled - Cannot verify token for user '{}'", id);
            return null;
        }

        if (id == null || !(credential instanceof PasswordCredential)) {
            LOGGER.debug("Invalid parameters for JWT verification - id: {}, credential type: {}",
                    id, credential != null ? credential.getClass().getSimpleName() : "null");
            return null;
        }

        LOGGER.debug("Starting JWT token verification for user '{}'", id);

        final char[] rawToken = ((PasswordCredential) credential).getPassword();

        // Check if the credential has JWT structure (3 parts separated by dots)
        final var tokenString = String.valueOf(rawToken);
        if (tokenString.split("\\.").length != 3) {
            LOGGER.debug("Credential for user '{}' is not a JWT token (expected 3 parts, got {})",
                    id, tokenString.split("\\.").length);
            return null;
        }

        final var ca = new ComparableAccount(new BaseAccount(id, null));

        var cacheCheckStartTime = System.currentTimeMillis();
        final var _cached = this.jwtCache.get(ca);
        var cacheCheckDuration = System.currentTimeMillis() - cacheCheckStartTime;

        // first check if the very same token is in the cache
        if (_cached != null && _cached.isPresent() && Arrays.equals(rawToken, _cached.get().raw())) {
            LOGGER.debug("JWT token found in cache for user '{}' - Cache lookup: {}ms", id, cacheCheckDuration);
            final var cached = _cached.get();
            final var roles = Sets.newHashSet(cached.roles());
            final var jwtParts = String.valueOf(cached.raw()).split("\\.");
            final var jwtPayload = new String(Base64.getUrlDecoder().decode(jwtParts[1]), StandardCharsets.UTF_8);

            var totalDuration = System.currentTimeMillis() - verificationStartTime;
            LOGGER.debug("JWT token verification successful (cached) for user '{}' with roles: {} - Total: {}ms",
                    id, roles, totalDuration);

            return new JwtAccount(id, roles, jwtPayload);
        } else {
            LOGGER.debug("JWT token not in cache for user '{}' - Performing verification - Cache lookup: {}ms",
                    id, cacheCheckDuration);
            // if the token is not in the cache, verify it
            try {
                var jwtVerifyStartTime = System.currentTimeMillis();
                final var decoded = this.verifier.verify(String.valueOf(rawToken));
                var jwtVerifyDuration = System.currentTimeMillis() - jwtVerifyStartTime;

                if (id.equals(decoded.getSubject())) {
                    final var _roles = decoded.getClaim(ROLES).asArray(String.class);
                    final var roles = Sets.newHashSet(_roles);

                    final var jwtPayload = new String(Base64.getUrlDecoder().decode(decoded.getPayload()),
                            StandardCharsets.UTF_8);

                    // Cache the token exactly as presented, so the fast path above can recognise
                    // it next time. Re-minting one here from `ca.wrapped()` — a bare BaseAccount
                    // with no roles and no properties — used to store a degraded impostor under
                    // this user's key, which get() would then hand back as their token.
                    var cacheUpdateStartTime = System.currentTimeMillis();
                    this.jwtCache.put(ca, new Token(
                            rawToken,
                            decoded.getExpiresAt(),
                            roles.toArray(new String[roles.size()]),
                            null));
                    var cacheUpdateDuration = System.currentTimeMillis() - cacheUpdateStartTime;

                    var totalDuration = System.currentTimeMillis() - verificationStartTime;
                    LOGGER.debug("JWT token verification successful for user '{}' with roles: {} - JWT verify: {}ms, Cache update: {}ms, Total: {}ms",
                            id, roles, jwtVerifyDuration, cacheUpdateDuration, totalDuration);

                    return new JwtAccount(id, roles, jwtPayload);
                } else {
                    var totalDuration = System.currentTimeMillis() - verificationStartTime;
                    LOGGER.warn("Invalid JWT token from user '{}' - Subject mismatch: expected '{}', got '{}' - Verification: {}ms, Total: {}ms",
                            id, id, decoded.getSubject(), jwtVerifyDuration, totalDuration);
                    return null;
                }
            } catch (final Exception e) {
                var totalDuration = System.currentTimeMillis() - verificationStartTime;
                LOGGER.warn("JWT token verification failed for user '{}' after {}ms: {}",
                        id, totalDuration, e.getMessage());
                return null;
            }
        }
    }

    @Override
    public Account verify(final Credential credential) {
        return null;
    }

    @Override
    public PasswordCredential get(final Account account) {
        return get(account, null);
    }

    @Override
    public PasswordCredential get(final Account account, final Request<?> request) {
        var tokenStartTime = System.currentTimeMillis();

        if (!enabled) {
            RequestPhaseContext.setPhase(Phase.ITEM);
            LOGGER.debug("JwtTokenManager is disabled - Cannot generate token");
            RequestPhaseContext.reset();
            return null;
        }

        if (account == null || account.getPrincipal() == null || account.getPrincipal().getName() == null) {
            RequestPhaseContext.setPhase(Phase.ITEM);
            LOGGER.debug("Invalid account provided to JwtTokenManager - Cannot generate token");
            RequestPhaseContext.reset();
            return null;
        }

        var userName = account.getPrincipal().getName();
        var userRoles = account.getRoles().stream().collect(java.util.stream.Collectors.toSet());

        LOGGER.debug("Generating JWT token for user '{}' with roles: {}", userName, userRoles);

        try {
            var cacheStartTime = System.currentTimeMillis();
            // The effective claim list is part of the cache key: on a multi-tenant node the same
            // principal can be served with different lists, producing different tokens.
            final var claims = JwtIssuer.claimsOverride(request);
            final var token = this.jwtCache.getLoading(new ComparableAccount(account, claims)).get();
            var cacheDuration = System.currentTimeMillis() - cacheStartTime;

            final var newTokenAccount = new PwdCredentialAccount(
                    account.getPrincipal().getName(),
                    token.raw(),
                    Sets.newTreeSet(account.getRoles()));

            var totalDuration = System.currentTimeMillis() - tokenStartTime;
            LOGGER.debug("JWT token generated for user '{}' - Cache lookup: {}ms, Total: {}ms", userName, cacheDuration, totalDuration);

            return newTokenAccount.getCredentials();
        } catch (Exception ex) {
            var totalDuration = System.currentTimeMillis() - tokenStartTime;
            LOGGER.error("Error generating JWT token for user '{}' after {}ms", userName, totalDuration, ex);
            throw ex;
        }
    }

    private Token newToken(final Account account, final List<String> claims) {
        return newToken(account, Date.from(Instant.now().plus(ttl, ChronoUnit.MINUTES)), claims);
    }

    private Token newToken(final Account account, final Date expires, final List<String> claims) {
        final var jwtIssuer = issuer();

        var builder = jwtIssuer.newBuilder(account.getPrincipal().getName(), account.getRoles(), expires);

        final Map<String, ? super Object> properties;

        if (account instanceof final WithProperties<?> awp) {
            // authDb is always included when present (the cache key matches on it)
            builder = jwtIssuer.applyAccountClaims(builder, awp.propertiesAsMap(), claims);
            properties = jwtIssuer.accountClaims(awp.propertiesAsMap(), claims);
        } else {
            properties = null;
        }

        final var raw = jwtIssuer.sign(builder);

        return new Token(
                raw.toCharArray(),
                expires,
                account.getRoles().toArray(new String[account.getRoles().size()]),
                properties);
    }

    private String getAuthDb(Account account) {
        return account instanceof WithProperties<?> wp ? JwtIssuer.authDb(wp.propertiesAsMap()) : null;
    }

    /**
     * Adds the configured {@code account-properties-claims} to the given JWT builder for
     * the given account. Returns the (possibly updated) builder for chaining.
     *
     * <p>This method is used by {@code OAuthAuthorizationService} when building the
     * authorization-code JWT so that account properties are propagated to the final
     * access token without duplicating the filtering logic.
     */
    public Builder withAccountPropertiesClaims(Builder builder, final Account account) {
        return withAccountPropertiesClaims(builder, account, null);
    }

    /**
     * As {@link #withAccountPropertiesClaims(Builder, Account)}, resolving the effective claim
     * list from {@code request} — see {@link JwtIssuer#CLAIMS_OVERRIDE_PARAM}.
     *
     * <p>Callers that hold a request should prefer this overload. An authorization code that
     * omits a claim cannot have it reappear in the access token minted from it: the access token
     * is built from the code's own payload, so a claim dropped here is lost for good.
     */
    public Builder withAccountPropertiesClaims(Builder builder, final Account account, final Request<?> request) {
        if (!(account instanceof WithProperties<?> awp)) return builder;
        return issuer().applyAccountClaims(builder, awp.propertiesAsMap(), JwtIssuer.claimsOverride(request));
    }

    @Override
    public void invalidate(final Account account) {
        if (!enabled)
            return;

        this.jwtCache.invalidate(new ComparableAccount(account));
    }

    @Override
    public void update(final Account account) {
        if (!enabled)
            return;

        final var ca = new ComparableAccount(account);
        this.jwtCache.put(ca, this.jwtCache.getLoading(ca).get());
    }

    @Override
    public void injectTokenHeaders(final HttpServerExchange exchange, final PasswordCredential token) {
        if (!enabled)
            return;

        final var request = Request.of(exchange);

        if (request.getAuthenticatedAccount() != null
                && request.getAuthenticatedAccount().getPrincipal() != null
                && request.getAuthenticatedAccount().getPrincipal().getName() != null) {
            final var account = request.getAuthenticatedAccount();
            // Same key get() uses: the effective claim list is part of the token's identity
            final var claims = JwtIssuer.claimsOverride(request);
            final var ca = new ComparableAccount(account, claims);

            exchange.getResponseHeaders().add(AUTH_TOKEN_LOCATION_HEADER,
                    URLUtils.removeTrailingSlashes(srvURI));

            // Check for renew parameter
            // - On /token or /token/cookie: use ?renew=true (new OAuth 2.0 style)
            // - On other endpoints: use ?renew-auth-token (legacy, requires allowLegacy config)
            var requestPath = exchange.getRequestPath();
            var isTokenEndpoint = "/token".equals(requestPath) || "/token/cookie".equals(requestPath);
            var shouldRenew = (isTokenEndpoint && exchange.getQueryParameters().containsKey("renew"))
                    || exchange.getQueryParameters().containsKey("renew-auth-token");

            if (shouldRenew) {
                final var newToken = newToken(account, claims);

                this.jwtCache.put(ca, newToken);
                exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, String.valueOf(newToken.raw()));
                exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, newToken.getDateAsString());
            } else if (this.jwtCache.get(ca) != null) {
                final var cachedToken = this.jwtCache.get(ca).get();
                exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, String.valueOf(cachedToken.raw()));
                exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, cachedToken.getDateAsString());
            } else {
                // Token was just generated but not yet in cache - use the one from the token parameter
                if (token != null) {
                    exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, String.valueOf(token.getPassword()));
                    // Get the cached token to get the expiry date
                    final var cachedToken = this.jwtCache.get(ca);
                    if (cachedToken != null && cachedToken.isPresent()) {
                        exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, cachedToken.get().getDateAsString());
                    }
                }
            }
        }
    }
}

record Token(char[] raw, Date expires, String[] roles, Map<String, ? super Object> properties) {
    public static Token fromJWT(final DecodedJWT jwt) {
        final var raw = jwt.getToken().toCharArray();
        final var expires = jwt.getExpiresAt();
        final var roles = jwt.getClaim(JwtTokenManager.ROLES).asArray(String.class);

        final var accountProperties = new HashMap<String, Object>();

        jwt.getClaims().entrySet().stream()
                .filter(e -> !e.getKey().equals("sub"))
                .filter(e -> !e.getKey().equals("iss"))
                .filter(e -> !e.getKey().equals(JwtTokenManager.ROLES))
                .forEach(e -> accountProperties.put(e.getKey(), e.getValue()));

        return new Token(raw, expires, roles, accountProperties);
    }

    public String getDateAsString() {
        return this.expires.toInstant().toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Token token))
            return false;

        return Arrays.equals(raw, token.raw) &&
                Objects.equals(expires, token.expires) &&
                Arrays.equals(roles, token.roles) &&
                Objects.equals(properties, token.properties);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(expires, properties);
        result = 31 * result + Arrays.hashCode(raw);
        result = 31 * result + Arrays.hashCode(roles);
        return result;
    }

    @Override
    public String toString() {
        return "Token{" +
                "raw=" + Arrays.toString(raw) +
                ", expires=" + expires +
                ", roles=" + Arrays.toString(roles) +
                ", properties=" + properties +
                '}';
    }
}

/**
 * Cache key for issued tokens.
 *
 * <p>{@code claims} is the effective {@code account-properties-claims} list the token was built
 * with — part of the identity of the cached token, not incidental. On a multi-tenant node two
 * requests for the same principal can carry different lists (see
 * {@link JwtIssuer#CLAIMS_OVERRIDE_PARAM}), and the resulting tokens differ in content; keying on
 * the principal alone would serve one tenant's token to another. {@code null} means "the
 * configured default was used".
 */
record ComparableAccount(Account wrapped, List<String> claims) {
    ComparableAccount(Account wrapped) {
        this(wrapped, null);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ComparableAccount that))
            return false;
        if (wrapped.getPrincipal() == null
                || wrapped.getPrincipal().getName() == null
                || that.wrapped.getPrincipal() == null
                || that.wrapped.getPrincipal().getName() == null) {
            return false;
        }

        // Compare username
        if (!Objects.equals(wrapped.getPrincipal().getName(), that.wrapped.getPrincipal().getName())) {
            return false;
        }

        // Compare the effective claim list: same user, different claims -> different token
        if (!Objects.equals(this.claims, that.claims)) {
            return false;
        }

        // Compare authDb if present in account properties
        String thisAuthDb = getAuthDb(this.wrapped);
        String thatAuthDb = getAuthDb(that.wrapped);
        return Objects.equals(thisAuthDb, thatAuthDb);
    }

    @Override
    public int hashCode() {
        String username = wrapped.getPrincipal() == null ? null : wrapped.getPrincipal().getName();
        String authDb = getAuthDb(wrapped);
        return Objects.hash(username, authDb, claims);
    }

    private String getAuthDb(Account account) {
        return account instanceof WithProperties<?> wp ? JwtIssuer.authDb(wp.propertiesAsMap()) : null;
    }
}
