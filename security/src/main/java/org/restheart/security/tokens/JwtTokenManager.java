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

import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.configuration.ConfigurationException;
import org.restheart.configuration.Utils;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.TokenManager;
import org.restheart.security.BaseAccount;
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

    @Inject("config")
    Map<String, Object> config;

    @OnInit
    public void init() throws ConfigurationException {
        this.enabled = true;

        this.srvURI = arg(config, "srv-uri");
        this.ttl = arg(config, "ttl");

        if (ttl < 1) {
            this.enabled = false;
            throw new ConfigurationException("TTL minimum value is 1 minute");
        }

        final String key = arg(config, "key");

        if ("secret".equals(key)) {
            LOGGER.warn("Using the default value: you should update the JWT key!");
        }

        this.algo = Algorithm.HMAC256((String) arg(config, "key"));
        this.issuer = arg(config, "issuer");

        jwtCache = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE,
                Cache.EXPIRE_POLICY.AFTER_WRITE,
                ttl * 1000 * 60 - 500, // -500 makes sure that cache entry expires always before token
                account -> newToken(account.wrapped));

        audience = argOrDefault(config, "audience", null);

        try {
            this.verifier = audience != null
                    ? JWT.require(algo).withIssuer(issuer).withAudience(audience).build()
                    : JWT.require(algo).withIssuer(issuer).build();

        } catch (final Exception e) {
            this.enabled = false;
            throw new ConfigurationException("error setting the verifier", e);
        }

        this.accountPropertiesClaims = argOrDefault(config, "account-properties-claims", null);
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
                    
                    var cacheUpdateStartTime = System.currentTimeMillis();
                    this.jwtCache.put(ca, newToken(ca.wrapped, decoded.getExpiresAt()));
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
        var tokenStartTime = System.currentTimeMillis();
        
        if (!enabled) {
            LOGGER.debug("JwtTokenManager is disabled - Cannot generate token");
            return null;
        }

        if (account == null || account.getPrincipal() == null || account.getPrincipal().getName() == null) {
            LOGGER.debug("Invalid account provided to JwtTokenManager - Cannot generate token");
            return null;
        }
        
        var userName = account.getPrincipal().getName();
        var userRoles = account.getRoles().stream().collect(java.util.stream.Collectors.toSet());
        
        LOGGER.debug("Generating JWT token for user '{}' with roles: {}", userName, userRoles);

        try {
            var cacheStartTime = System.currentTimeMillis();
            final var token = this.jwtCache.getLoading(new ComparableAccount(account)).get();
            var cacheDuration = System.currentTimeMillis() - cacheStartTime;
            
            final var newTokenAccount = new PwdCredentialAccount(
                    account.getPrincipal().getName(),
                    token.raw(),
                    Sets.newTreeSet(account.getRoles()));

            var totalDuration = System.currentTimeMillis() - tokenStartTime;
            LOGGER.debug("JWT token generated for user '{}' - Cache lookup: {}ms, Total: {}ms", 
                userName, cacheDuration, totalDuration);
                
            return newTokenAccount.getCredentials();
        } catch (Exception ex) {
            var totalDuration = System.currentTimeMillis() - tokenStartTime;
            LOGGER.error("Error generating JWT token for user '{}' after {}ms", userName, totalDuration, ex);
            throw ex;
        }
    }

    private Token newToken(final Account account) {
        return newToken(account, Date.from(Instant.now().plus(ttl, ChronoUnit.MINUTES)));
    }

    private Token newToken(final Account account, final Date expires) {
        final var creator = audience != null
                ? JWT.create().withIssuer(issuer).withAudience(audience)
                : JWT.create().withIssuer(issuer);

        final var _builder = creator
                .withSubject(account.getPrincipal().getName())
                .withExpiresAt(expires)
                .withIssuer(issuer)
                .withArrayClaim(ROLES, account.getRoles().toArray(new String[account.getRoles().size()]));

        final Builder[] builder = { _builder };
        final Map<String, ? super Object> properties;

        if (account instanceof final WithProperties<?> awp) {
            properties = claimsFromAccountProps(awp.propertiesAsMap());
            properties.entrySet().stream().forEach(e -> builder[0] = withClaim(builder[0], e.getKey(), e.getValue()));
        } else {
            properties = null;
        }

        final var raw = builder[0].sign(algo);

        return new Token(
                raw.toCharArray(),
                expires,
                account.getRoles().toArray(new String[account.getRoles().size()]),
                properties);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Builder withClaim(final Builder b, final String k, final Object v) {
        if (k == null || v == null) {
            return b;
        }

        return switch (v) {
            case final String s -> b.withClaim(k, s);
            case final String[] ss -> b.withArrayClaim(k, ss);
            case final Boolean boo -> b.withClaim(k, boo);
            case final Integer i -> b.withClaim(k, i);
            case final Integer[] ii -> b.withArrayClaim(k, ii);
            case final Long l -> b.withClaim(k, l);
            case final Long[] ll -> b.withArrayClaim(k, ll);
            case final Double d -> b.withClaim(k, d);
            case final Date d -> b.withClaim(k, d);
            case final Map m -> {
                try {
                    yield b.withClaim(k, (Map<String, ?>) m);
                } catch (final ClassCastException cce) {
                    LOGGER.warn(ERROR_UNSUPPORTED_JWT_CLAIM_TYPE, k);
                    yield b;
                }
            }
            case final List l -> b.withClaim(k, (List<?>) l);
            default -> {
                LOGGER.warn(ERROR_UNSUPPORTED_JWT_CLAIM_TYPE, k, v.getClass().getSimpleName());
                yield b;
            }
        };
    }

    private Map<String, ? super Object> claimsFromAccountProps(final Map<String, ? super Object> properties) {
        final var ret = new HashMap<String, Object>();

        if (accountPropertiesClaims != null) {
            this.accountPropertiesClaims.stream()
                    .map(path -> new Pair<String[], Object>(keysFromPath(path), Utils.find(properties, path, true)))
                    .filter(p -> p.getValue() != null)
                    .forEach(p -> addClaim(ret, p.getKey(), p.getValue()));
        }

        return ret;
    }

    private String[] keysFromPath(final String path) {
        final var ret = path.contains("/") ? path.split("/") : new String[] { path };
        // remove empty elements
        return Arrays.stream(ret).filter(k -> k != null && !k.isBlank()).toArray(String[]::new);
    }

    /**
     * add the claim preserving the json structure
     * i.e. /a/nested/value -> { a: { nested: value} }
     * 
     * @param map
     * @param keys
     * @param val
     */
    private void addClaim(final Map<String, Object> map, final String[] keys, final Object val) {
        for (var idx = 0; idx < keys.length; idx++) {
            if (idx == keys.length - 1) {
                map.put(keys[idx], val);
            } else {
                final var nestedMap = new HashMap<String, Object>();
                map.put(keys[idx], nestedMap);
                addClaim(nestedMap, Arrays.copyOfRange(keys, idx + 1, keys.length), val);
                break;
            }
        }
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
            final var ca = new ComparableAccount(account);

            final var cid = request.getAuthenticatedAccount().getPrincipal().getName();

            exchange.getResponseHeaders().add(AUTH_TOKEN_LOCATION_HEADER,
                    URLUtils.removeTrailingSlashes(srvURI).concat("/").concat(cid));

            if (exchange.getQueryParameters().containsKey("renew-auth-token")) {
                final var newToken = newToken(account);

                this.jwtCache.put(ca, newToken);
                exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, String.valueOf(newToken.raw()));
                exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, newToken.getDateAsString());
            } else if (this.jwtCache.get(ca) != null) {
                final var cachedToken = this.jwtCache.get(ca).get();
                exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, String.valueOf(cachedToken.raw()));
                exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, cachedToken.getDateAsString());
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
        if (!(o instanceof Token))
            return false;
        final Token token = (Token) o;
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

class ComparableAccount {
    public final Account wrapped;

    public ComparableAccount(final Account wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ComparableAccount))
            return false;
        final ComparableAccount that = (ComparableAccount) o;
        if (wrapped.getPrincipal() == null
                || wrapped.getPrincipal().getName() == null
                || that.wrapped.getPrincipal() == null
                || that.wrapped.getPrincipal().getName() == null) {
            return false;
        } else {
            return Objects.equals(wrapped.getPrincipal().getName(), that.wrapped.getPrincipal().getName());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrapped.getPrincipal() == null ? null : wrapped.getPrincipal().getName());
    }
}
