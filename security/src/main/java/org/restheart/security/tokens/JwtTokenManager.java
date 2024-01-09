/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.JWTCreator.Builder;
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

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private Algorithm algo;

    private String srvURI = "/tokens";
    private int ttl = 15;
    private String issuer = "restheart.org";
    private String[] audience;

    private static final int MAX_CACHE_SIZE = 1000;

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

        String key = arg(config, "key");

        if ("secret".equals(key)) {
            LOGGER.warn("You should really update the JWT key!");
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

        } catch (Throwable t) {
            this.enabled = false;
            LOGGER.error("error", t);
            throw new ConfigurationException("error ");
        }

        this.accountPropertiesClaims = argOrDefault(config, "account-properties-claims", null);
    }

    @Override
    public Account verify(final Account account) {
        return enabled ? account : null;
    }

    @Override
    public Account verify(final String id, final Credential credential) {
        if (!enabled) { return null; }

        if (id != null && credential instanceof PasswordCredential) {
            char[] rawToken = ((PasswordCredential) credential).getPassword();

            var ca = new ComparableAccount(new BaseAccount(id, null));

            var _cached = this.jwtCache.get(ca);

            // first check if the very same token is in the cache
            if (_cached != null && _cached.isPresent() && Arrays.equals(rawToken, _cached.get().raw())) {
                LOGGER.debug("jwt token in cache");
                var cached = _cached.get();
                var roles = Sets.newHashSet(cached.roles());

                var jwtParts = new String(cached.raw()).split("\\.");

                var jwtPayload = new String(Base64.getUrlDecoder().decode(jwtParts[1]), Charset.forName("UTF-8"));

                return new JwtAccount(id, roles, jwtPayload);
            } else {
                LOGGER.trace("jwt token not in cache, let's verify it");
                // if the token is not in the cache, verify it
                try {
                    var decoded = this.verifier.verify(new String(rawToken));

                    if (id.equals(decoded.getSubject())) {
                        var _roles = decoded.getClaim("roles").asArray(String.class);
                        var roles = Sets.newHashSet(_roles);

                        var jwtPayload = new String(Base64.getUrlDecoder().decode(decoded.getPayload()), Charset.forName("UTF-8"));
                        this.jwtCache.put(ca, newToken(ca.wrapped, decoded.getExpiresAt()));
                        return new JwtAccount(id, roles, jwtPayload);
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

        var newTokenAccount = new PwdCredentialAccount(account.getPrincipal().getName(), token.raw(), Sets.newTreeSet(account.getRoles()));

        return newTokenAccount.getCredentials();
    }

    private Token newToken(Account account) {
        return newToken(account, Date.from(Instant.now().plus(ttl, ChronoUnit.MINUTES)));
    }

    private Token newToken(Account account, Date expires) {
        var creator = audience != null
            ? JWT.create().withIssuer(issuer).withAudience(audience)
            : JWT.create().withIssuer(issuer);

        var _builder = creator
            .withSubject(account.getPrincipal().getName())
            .withExpiresAt(expires)
            .withIssuer(issuer)
            .withArrayClaim("roles", account.getRoles().toArray(new String[account.getRoles().size()]));

        Builder[] builder = { _builder };

        final Map<String, ? super Object> properties;

        if (account instanceof WithProperties<?> awp) {
            properties = claimsFromAccountProps(awp.propertiesAsMap());
            properties.entrySet().stream().forEach(e -> builder[0] = withClaim(builder[0], e.getKey(), e.getValue()));
        } else {
            properties = null;
        }

        var raw = builder[0].sign(algo);

        return new Token(raw.toCharArray(), expires, account.getRoles().toArray(new String[0]), properties);
    }


    @SuppressWarnings("unchecked")
    private Builder withClaim(Builder b, String k, Object v) {
        if (k == null || v == null) {
            return b;
        } if (v instanceof String s) {
            return b.withClaim(k, s);
        } else if (v instanceof String[] ss) {
            return b.withArrayClaim(k, ss);
        }else if (v instanceof Boolean boo) {
            return b.withClaim(k, boo);
        } else if (v instanceof Integer i) {
            return b.withClaim(k, i);
        } else if (v instanceof Integer[] ii) {
            return b.withArrayClaim(k, ii);
        } else if (v instanceof Long l) {
            return b.withClaim(k, l);
        } else if (v instanceof Long[] ll) {
            return b.withArrayClaim(k, ll);
        } else if (v instanceof Double d) {
            return b.withClaim(k, d);
        } else if (v instanceof Date d) {
            return b.withClaim(k, d);
        } else if (v instanceof Map m) {
            try {
                return b.withClaim(k, (Map<String, ?>) m);
            } catch(ClassCastException cce) {
                LOGGER.warn("cannot add claim {} to jwt because of usupported type", k);
                return b;
            }
        } else if (v instanceof List l) {
            return b.withClaim(k, (List<?>) l);
        } else {
            LOGGER.warn("cannot add claim {} to jwt because of usupported type", k, v.getClass().getSimpleName());
            return b;
        }
    }

    private Map<String, ? super Object> claimsFromAccountProps(Map<String, ? super Object> properties) {
        var ret = new HashMap<String, Object>();

        if (accountPropertiesClaims != null) {
            this.accountPropertiesClaims.stream()
            .map(path -> new Pair<String[], Object>(keysFromPath(path), Utils.find(properties, path, true)))
            .filter(p -> p.getValue() != null)
            .forEach(p -> addClaim(ret, p.getKey(), p.getValue()));
        }

        return ret;
    }

    private String[] keysFromPath(String path) {
        var ret = path.contains("/")
            ? path.split("/")
            : new String[] { path } ;

        // remove empty elements
        return Arrays.stream(ret).filter(k -> k != null && !k.isBlank()).toArray(String[]::new);
    }

    /**
     * add the claim preserving the json structure
     * i.e. /a/nested/value -> { a: { nested: value} }
     * @param map
     * @param keys
     * @param val
     */
    private void addClaim(Map<String, Object> map, String[] keys, Object val) {
        for (var idx = 0; idx < keys.length; idx++) {
            if (idx == keys.length-1) {
                map.put(keys[idx], val);
            } else {
                var nestedMap = new HashMap<String, Object>();
                map.put(keys[idx], nestedMap);
                addClaim(nestedMap, Arrays.copyOfRange(keys, idx+1, keys.length), val);
                break;
            }
        }
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
                exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, new String(newToken.raw()));
                exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, newToken.getDateAsString());
            } else if (this.jwtCache.get(ca) != null) {
                var cachedToken = this.jwtCache.get(ca).get();
                exchange.getResponseHeaders().add(AUTH_TOKEN_HEADER, new String(cachedToken.raw()));
                exchange.getResponseHeaders().add(AUTH_TOKEN_VALID_HEADER, cachedToken.getDateAsString());
            }
        }
    }
}

record Token(char[] raw, Date expires, String[] roles, Map<String, ? super Object> properties) {
    public static Token fromJWT(DecodedJWT jwt) {
        var raw = jwt.getToken().toCharArray();
        var expires = jwt.getExpiresAt();
        var roles = jwt.getClaim("roles").asArray(String.class);

        var accountProperties = new HashMap<String, Object>();

        jwt.getClaims().entrySet().stream()
            .filter(e -> !e.getKey().equals("sub"))
            .filter(e -> !e.getKey().equals("iss"))
            .filter(e -> !e.getKey().equals("roles"))
            .forEach(e -> accountProperties.put(e.getKey(), e.getValue()));

        return new Token(raw, expires, roles, accountProperties);
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
