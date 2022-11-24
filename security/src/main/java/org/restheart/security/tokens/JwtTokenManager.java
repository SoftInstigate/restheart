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

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.Sets;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;

import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.TokenManager;
import org.restheart.security.BaseAccount;
import org.restheart.security.PwdCredentialAccount;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import static org.restheart.plugins.ConfigurablePlugin.argValue;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "jwtTokenManager",
                description = "issues and verifies auth tokens in a cluster compatible way",
                enabledByDefault = false)
public class JwtTokenManager implements TokenManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenManager.class);

    private LoadingCache<ComparableAccount, Token> jwtCache;

    private JWTVerifier verifier;
    private JWTCreator.Builder creator;
    private Algorithm algo;

    private String srvURI = "/tokens";
    private int ttl = 15;
    private String issuer = "restheart.com";

    private static final int MAX_CACHE_SIZE = 1000;

    private boolean enabled = false;

    @Inject("config")
    Map<String, Object> config;

    @OnInit
    public void init() throws ConfigurationException {
        this.enabled = true;

        this.srvURI = argValue(config, "srv-uri");
        this.ttl = argValue(config, "ttl");

        if (ttl < 1) {
            this.enabled = false;
            throw new ConfigurationException("TTL minimum value is 1 minute");
        }

        this.algo = Algorithm.HMAC256((String) argValue(config, "key"));
        this.issuer = argValue(config, "issuer");

        jwtCache = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE,
            Cache.EXPIRE_POLICY.AFTER_WRITE,
            ttl * 1000 * 60 - 500, // -500 makes sure that cache entry expires always before token
            account -> newToken(account.wrapped));

        try {
            this.verifier = JWT.require(algo).withIssuer(issuer).build();
            this.creator = JWT.create().withIssuer(issuer);
        } catch (Throwable t) {
            this.enabled = false;
            LOGGER.error("error", t);
            throw new ConfigurationException("error ");
        }
    }

    @Override
    public Account verify(final Account account) {
        return enabled ? account : null;
    }

    @Override
    public Account verify(final String id, final Credential credential) {
        if (!enabled) { return null; }

        if (id != null && credential instanceof PasswordCredential) {
            char[] token = ((PasswordCredential) credential).getPassword();

            var ca = new ComparableAccount(new BaseAccount(id, null));

            // first check if the very same token is in the cache
            if (this.jwtCache.get(ca) != null && this.jwtCache.get(ca).isPresent() && Arrays.equals(token, this.jwtCache.get(ca).get().raw)) {
                LOGGER.debug("jwt token in cache");
                return new PwdCredentialAccount(id, token, Sets.newHashSet(this.jwtCache.get(ca).get().roles));
            } else {
                LOGGER.trace("jwt token not in cache, let's verify it");
                // if the token is not in the cache, verify it
                try {
                    var decoded = this.verifier.verify(new String(token));

                    if (id.equals(decoded.getSubject())) {
                        var roles = decoded.getClaim("roles").asArray(String.class);

                        LOGGER.trace("******************** valid token from user {}", id);

                        return new PwdCredentialAccount(id, token, Sets.newHashSet(roles));
                    } else {
                        LOGGER.warn("invalid token from user {}, not matching id in token, was {}", id, decoded.getSubject());
                        return null;
                    }
                } catch (Throwable t) {
                    LOGGER.debug("expired or invalid token from user {}, {}", id, t.getMessage());
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    @Override
    public Account verify(final Credential credential) {
        return null;
    }

    @Override
    public PasswordCredential get(Account account) {
        if (!enabled) {
            return null;
        }

        if (account == null || account.getPrincipal() == null || account.getPrincipal().getName() == null) {
            return  null;
        }

        var token = this.jwtCache.getLoading(new ComparableAccount(account)).get();

        PwdCredentialAccount newTokenAccount = new PwdCredentialAccount(
            account.getPrincipal().getName(),
            token.raw,
            Sets.newTreeSet(account.getRoles()));

        return newTokenAccount.getCredentials();
    }

    private Token newToken(Account account) {
        var expires = Date.from(Instant.now().plus(ttl, ChronoUnit.MINUTES));

        var raw= this.creator
            .withSubject(account.getPrincipal().getName())
            .withExpiresAt(expires)
            .withIssuer(issuer)
            .withArrayClaim("roles", account.getRoles().toArray(new String[account.getRoles().size()]))
            .sign(algo);

        return new Token(raw.toCharArray(), expires, account.getRoles().toArray(new String[0]));
    }

    @Override
    public void invalidate(Account account) {
        if (!enabled) { return; }

        this.jwtCache.invalidate(new ComparableAccount(account));
    }

    @Override
    public void update(Account account) {
        if (!enabled) { return; }

        var ca = new ComparableAccount(account);
        this.jwtCache.put(ca, this.jwtCache.getLoading(ca).get());
    }

    @Override
    public void injectTokenHeaders(HttpServerExchange exchange, PasswordCredential token) {
        if (!enabled) { return; }

        var request = Request.of(exchange);

        if (request.getAuthenticatedAccount() != null
                && request.getAuthenticatedAccount().getPrincipal() != null
                && request.getAuthenticatedAccount().getPrincipal().getName() != null) {
            var account = request.getAuthenticatedAccount();
            var ca = new ComparableAccount(account);

            var cid = request.getAuthenticatedAccount().getPrincipal().getName();

            exchange.getResponseHeaders().add(AUTH_TOKEN_LOCATION_HEADER, URLUtils.removeTrailingSlashes(srvURI).concat("/").concat(cid));

            if (exchange.getQueryParameters().containsKey("renew-auth-token")) {
                var newToken = newToken(account);

                this.jwtCache.put(ca, newToken);
                exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, new String(newToken.raw));
                exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, newToken.getDateAsString());
            } else if (this.jwtCache.get(ca) != null) {
                var cachedToken = this.jwtCache.get(ca).get();
                exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, new String(cachedToken.raw));
                exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, cachedToken.getDateAsString());
            }
        }
    }
}

class Token {
    final public char[] raw;
    final public Date expires;
    final public String[] roles;

    public Token(char[] raw, Date expires, String[] roles) {
        this.raw = raw;
        this.expires = expires;
        this.roles = roles;
    }

    public Token(DecodedJWT jwt) {
        this.raw = jwt.getToken().toCharArray();
        this.expires = jwt.getExpiresAt();
        this.roles = jwt.getClaim("roles").asArray(String.class);;
    }

    public String getDateAsString() {
        return this.expires.toInstant().toString();
    }
}

class ComparableAccount {
    public final Account wrapped;

    public ComparableAccount(Account wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof ComparableAccount)) return false;
        ComparableAccount that = (ComparableAccount) o;
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
        return Objects.hash(wrapped.getPrincipal() == null
            ? null
            : wrapped.getPrincipal().getName());
    }
}
