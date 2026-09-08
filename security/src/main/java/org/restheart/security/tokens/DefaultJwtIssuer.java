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
package org.restheart.security.tokens;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonString;
import org.restheart.exchange.Request;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.security.BaseAccount;
import org.restheart.security.WithProperties;
import org.restheart.security.authenticators.MongoRealmAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;

/**
 * The single implementation of RESTHeart's JWT issuance policy, and of the framework-level
 * {@link org.restheart.plugins.security.JwtIssuer} capability that exposes it — see
 * {@link JwtIssuerProvider}, which publishes one instance of this class as the {@code jwtIssuer}
 * provider so that a plugin in any module can mint a token without a second copy of these rules
 * or of the signing key.
 *
 * <p>A JWT issued by RESTHeart is one thing, regardless of <em>when</em> it is issued: at
 * login by {@code restheart-accounts}, on {@code /token} by {@link JwtTokenManager}, or as an
 * authorization code by {@code OAuthAuthorizationService}. Before this class each of those
 * carried its own copy of the rules and they had silently diverged — different claim lists,
 * a denylist on one path only, nested-path support on one path only. Everything that decides
 * <em>what a RESTHeart JWT contains</em> lives here, so the three moments cannot drift apart
 * again.
 *
 * <p>This is deliberately <em>not</em> a {@code TokenManager}: the configured token manager is
 * pluggable and need not issue JWTs at all (see {@code RndTokenManager}, which issues opaque
 * random tokens). Components that must issue a JWT specifically share this policy without
 * assuming anything about which token manager is installed.
 *
 * <h2>Claim selection</h2>
 * <p>Only the account properties named by the effective {@code account-properties-claims} list
 * become claims. A name may be a nested path using {@code /} — {@code consents/tos/version}
 * selects that nested value and rebuilds the nesting in the token.
 *
 * <h2>Denylist</h2>
 * <p>{@link #DEFAULT_DENYLIST} plus the configured password property are refused whatever the
 * claim list says. A JWT payload is base64, <em>not</em> encrypted: it is readable by any client
 * holding the token. The filter is applied here, at issuance, rather than where the list is
 * written, so that no caller — including a tenant supplying a per-request override — can bypass
 * it by supplying its own list.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.7.0
 */
public class DefaultJwtIssuer implements org.restheart.plugins.security.JwtIssuer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJwtIssuer.class);

    private static final String ERROR_UNSUPPORTED_JWT_CLAIM_TYPE = "Cannot add claim {} to jwt because of unsupported type";

    /** Fields never eligible to become a claim: one-shot credentials. */
    public static final Set<String> DEFAULT_DENYLIST = Set.of(
            "emailVerificationToken", "emailVerificationCreatedAt",
            "passwordResetToken", "passwordResetCreatedAt");

    /** Used when {@code mongoRealmAuthenticator/prop-password} cannot be resolved. */
    public static final String DEFAULT_PASSWORD_PROPERTY = "password";

    /**
     * Attached-param carrying the effective {@code account-properties-claims} for this request,
     * overriding the configured default. Set by a deployment-layer interceptor that knows which
     * tenant the request belongs to.
     *
     * <p>The canonical name lives here so that every issuer reads the same key —
     * {@code RequestOverrides.ACCOUNT_PROPERTIES_CLAIMS} in {@code restheart-accounts} refers to
     * this constant rather than repeating the string.
     */
    public static final String CLAIMS_OVERRIDE_PARAM = "override-accounts-account-properties-claims";

    /**
     * The effective claim list for {@code request}: the {@link #CLAIMS_OVERRIDE_PARAM} attached
     * param when present and non-empty, otherwise {@code null} meaning "use the configured default".
     */
    @SuppressWarnings("unchecked")
    public static List<String> claimsOverride(org.restheart.exchange.Request<?> request) {
        if (request == null) {
            return null;
        }

        var v = request.attachedParam(CLAIMS_OVERRIDE_PARAM);

        return v instanceof List<?> l && !l.isEmpty() ? (List<String>) l : null;
    }

    /** Applied by {@link #issue(BaseAccount)} when a caller states no lifetime of its own. */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final Algorithm algo;
    private final String issuer;
    private final String[] audience;
    private final List<String> defaultClaims;
    private final Set<String> requiredClaims;
    private final Set<String> denylist;
    private final Duration defaultTtl;

    /**
     * @param algo          signing algorithm, already built from the shared key
     * @param issuer        the {@code iss} claim
     * @param audience      the {@code aud} claim, or {@code null} for none
     * @param defaultClaims account properties to copy into the token when no per-request
     *                      override applies; {@code null} means none
     * @param passwordProperty name of the password property to deny, in addition to
     *                      {@link #DEFAULT_DENYLIST}; {@code null} falls back to
     *                      {@value #DEFAULT_PASSWORD_PROPERTY}
     */
    public DefaultJwtIssuer(Algorithm algo, String issuer, String[] audience,
                            List<String> defaultClaims, String passwordProperty) {
        this(algo, issuer, audience, defaultClaims, null, passwordProperty);
    }

    /**
     * @param requiredClaims account properties always copied into the token, whatever the effective
     *                       claim list — see {@link #accountClaims(Map, List)}; {@code null} means
     *                       none
     */
    public DefaultJwtIssuer(Algorithm algo, String issuer, String[] audience,
                            List<String> defaultClaims, List<String> requiredClaims, String passwordProperty) {
        this(algo, issuer, audience, defaultClaims, requiredClaims, passwordProperty, DEFAULT_TTL);
    }

    /**
     * @param defaultTtl lifetime applied by {@link #issue(BaseAccount)}; {@code null} falls back to
     *                   {@link #DEFAULT_TTL}. Only the no-lifetime overload reads it — every caller
     *                   passing its own {@code ttl}, or its own {@code expires}, is unaffected.
     */
    public DefaultJwtIssuer(Algorithm algo, String issuer, String[] audience,
                            List<String> defaultClaims, List<String> requiredClaims, String passwordProperty,
                            Duration defaultTtl) {
        this.algo = algo;
        this.issuer = issuer;
        this.audience = audience;
        this.defaultClaims = defaultClaims;
        this.requiredClaims = requiredClaims == null ? Set.of() : Set.copyOf(requiredClaims);
        this.defaultTtl = defaultTtl == null ? DEFAULT_TTL : defaultTtl;

        var pwd = passwordProperty == null || passwordProperty.isBlank()
                ? DEFAULT_PASSWORD_PROPERTY
                : passwordProperty;

        var dl = new java.util.HashSet<>(DEFAULT_DENYLIST);
        dl.add(pwd);
        this.denylist = Set.copyOf(dl);
    }

    /** The claim list applied when a caller passes no override. */
    public List<String> defaultClaims() {
        return defaultClaims;
    }

    /** Whether {@code claim} is refused whatever the claim list says. */
    public boolean isDenylisted(String claim) {
        return denylist.contains(claim);
    }

    /**
     * Selects, from {@code properties}, the values named by the effective claim list, honouring
     * nested {@code a/b/c} paths and the denylist. The returned map is the claim structure as it
     * will appear in the token.
     *
     * @param properties account properties, or the user document — may be {@code null}
     * @param claimsOverride effective list for this issuance; {@code null} uses {@link #defaultClaims()}
     */
    public Map<String, Object> accountClaims(Map<String, ? super Object> properties, List<String> claimsOverride) {
        final var ret = new HashMap<String, Object>();

        if (properties == null) {
            return ret;
        }

        var configured = claimsOverride != null ? claimsOverride : defaultClaims;

        // Required claims are added whatever the effective list says. They are infrastructure the
        // deployment depends on — e.g. on a multi-tenant node a claim naming the node that issued
        // the token, checked on every subsequent request — so a tenant supplying its own list must
        // not be able to drop them and lock itself out. The denylist still wins over this.
        var claims = new java.util.LinkedHashSet<String>();
        claims.addAll(requiredClaims);
        if (configured != null) {
            claims.addAll(configured);
        }

        if (claims.isEmpty()) {
            return ret;
        }

        for (var path : claims) {
            if (path == null || path.isBlank()) {
                continue;
            }

            var keys = keysFromPath(path);

            if (Arrays.stream(keys).anyMatch(this::isDenylisted)) {
                LOGGER.debug("Refusing denylisted claim '{}': it is a credential and a JWT payload is readable by the client", path);
                continue;
            }

            var value = valueAt(properties, keys);

            if (value != null) {
                addClaim(ret, keys, value);
            }
        }

        return ret;
    }

    /**
     * Adds to {@code builder} the {@code authDb} claim (when present in {@code properties}) and
     * the claims selected by {@link #accountClaims(Map, List)}.
     *
     * @return the updated builder, for chaining
     */
    public Builder applyAccountClaims(Builder builder, Map<String, ? super Object> properties, List<String> claimsOverride) {
        if (properties == null) {
            return builder;
        }

        var authDb = authDb(properties);
        if (authDb != null) {
            builder = builder.withClaim("authDb", authDb);
        }

        for (var e : accountClaims(properties, claimsOverride).entrySet()) {
            builder = withClaim(builder, e.getKey(), e.getValue());
        }

        return builder;
    }

    /**
     * Issues a signed JWT.
     *
     * @param subject     the {@code sub} claim — the user identity
     * @param roles       the {@code roles} claim
     * @param expires     the {@code exp} claim
     * @param properties  account properties (or user document) to select claims from; may be {@code null}
     * @param extraClaims claims always included, bypassing the claim list but <em>not</em> the
     *                    denylist — e.g. the active team; may be {@code null}
     * @param claimsOverride effective claim list for this issuance; {@code null} uses the default
     */
    public String issue(String subject,
                        Set<String> roles,
                        Date expires,
                        Map<String, ? super Object> properties,
                        Map<String, ?> extraClaims,
                        List<String> claimsOverride) {
        return issue(subject, roles, expires, properties, extraClaims, claimsOverride, null);
    }

    /**
     * Same as {@link #issue(String, Set, Date, Map, Map, List)}, with an explicit {@code iss}.
     *
     * @param issuerOverride the {@code iss} claim to stamp instead of the configured one;
     *                       {@code null} uses the configured one — see
     *                       {@link org.restheart.plugins.security.JwtIssuer#issue(BaseAccount, Duration, String)}
     *                       for when a token needs an issuer that isn't this deployment's
     */
    public String issue(String subject,
                        Set<String> roles,
                        Date expires,
                        Map<String, ? super Object> properties,
                        Map<String, ?> extraClaims,
                        List<String> claimsOverride,
                        String issuerOverride) {
        var builder = newBuilder(subject, roles, expires, issuerOverride);

        builder = applyAccountClaims(builder, properties, claimsOverride);

        if (extraClaims != null) {
            for (var e : extraClaims.entrySet()) {
                if (isDenylisted(e.getKey())) {
                    LOGGER.warn("Refusing denylisted claim '{}' supplied as an extra claim", e.getKey());
                    continue;
                }
                builder = withClaim(builder, e.getKey(), e.getValue());
            }
        }

        return builder.sign(algo);
    }

    /**
     * The {@link org.restheart.plugins.security.JwtIssuer} entry point: everything the account
     * itself already carries — subject, roles, and whatever claims the configured policy selects
     * from its properties — with the lifetime the caller asks for. Same policy, same denylist and
     * same signature as a token issued at login or on {@code /token}: it is the identical JWT,
     * differing only in how long it lasts.
     */
    @Override
    public String issue(BaseAccount account, Duration ttl) {
        return issue(account, ttl, (String) null);
    }

    @Override
    public String issue(BaseAccount account) {
        return issue(account, defaultTtl, (String) null);
    }

    @Override
    public String issue(BaseAccount account, Duration ttl, String issuerOverride) {
        return issue(account, ttl, issuerOverride, null);
    }

    @Override
    public String issue(BaseAccount account, Duration ttl, Request<?> request) {
        return issue(account, ttl, null, request);
    }

    private String issue(BaseAccount account, Duration ttl, String issuerOverride, Request<?> request) {
        var properties = account instanceof WithProperties<?> wp ? wp.propertiesAsMap() : null;

        return issue(account.getPrincipal().getName(),
                account.getRoles(),
                Date.from(Instant.now().plus(ttl)),
                properties,
                null,
                claimsOverride(request),
                issuerOverride);
    }

    /**
     * A JWT builder carrying the shared identity of this deployment ({@code iss}, {@code aud},
     * {@code jti}) plus {@code sub}, {@code roles}, {@code iat} and {@code exp}. For callers that
     * need to add their own claims before signing.
     *
     * <p>{@code iat} matters most for a short-lived token: without it a holder sees an imminent
     * {@code exp} but cannot tell whether the window was a minute or a day, which is exactly what
     * it needs to know to decide whether the token is still worth using.
     */
    public Builder newBuilder(String subject, Set<String> roles, Date expires) {
        return newBuilder(subject, roles, expires, null);
    }

    /**
     * Same as {@link #newBuilder(String, Set, Date)}, with an explicit {@code iss}.
     *
     * @param issuerOverride the {@code iss} claim to stamp instead of the configured one;
     *                       {@code null} uses the configured one
     */
    public Builder newBuilder(String subject, Set<String> roles, Date expires, String issuerOverride) {
        var iss = issuerOverride == null ? this.issuer : issuerOverride;

        var creator = audience != null
                ? com.auth0.jwt.JWT.create().withIssuer(iss).withAudience(audience)
                : com.auth0.jwt.JWT.create().withIssuer(iss);

        return creator
                .withSubject(subject)
                .withIssuedAt(Instant.now())
                .withExpiresAt(expires)
                .withJWTId(java.util.UUID.randomUUID().toString())
                .withArrayClaim("roles", roles == null ? new String[0] : roles.toArray(String[]::new));
    }

    /** Signs a builder with this issuer's algorithm. */
    public String sign(Builder builder) {
        return builder.sign(algo);
    }

    /**
     * Adds a claim, mapping the Java type to the corresponding JWT type. Unsupported types are
     * logged and skipped rather than failing the whole issuance.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Builder withClaim(final Builder b, final String k, final Object v) {
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

    /**
     * Navigates {@code properties} following {@code keys}, returning the value found or
     * {@code null}.
     *
     * <p>Deliberately a plain map walk rather than {@code Utils.find}'s JXPath: that helper is
     * meant for the YAML configuration tree, and it resolves against account properties only for
     * some account types — {@code FileRealmAccount} hands back the map it parsed, whereas
     * {@code MongoRealmAccount} rebuilds it through GSON. Worse, it swallows every failure
     * ({@code catch (Throwable)} with {@code silent = true}), so a claim silently vanished from
     * the token with nothing in the logs. A map lookup has none of those problems and is what
     * this actually needs.
     */
    static Object valueAt(Map<String, ? super Object> properties, String[] keys) {
        Object current = properties;

        for (var key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }

            current = map.get(key);

            if (current == null) {
                return null;
            }
        }

        return current;
    }

    /** Splits {@code a/b/c} into its keys, dropping empty segments. */
    static String[] keysFromPath(final String path) {
        var ret = path.contains("/") ? path.split("/") : new String[]{path};
        return Arrays.stream(ret).filter(k -> k != null && !k.isBlank()).toArray(String[]::new);
    }

    /** Reads the {@code authDb} property, accepting both {@code String} and {@code BsonString}. */
    static String authDb(Map<String, ? super Object> properties) {
        if (properties == null || !properties.containsKey("authDb")) {
            return null;
        }

        return switch (properties.get("authDb")) {
            case String s -> s;
            case BsonString bs -> bs.getValue();
            case null, default -> null;
        };
    }

    /**
     * Adds a claim preserving the JSON structure implied by its path:
     * {@code a/nested/value} becomes <code>{ a: { nested: value } }</code>.
     */
    static void addClaim(final Map<String, Object> map, final String[] keys, final Object val) {
        for (var idx = 0;idx < keys.length;idx++) {
            if (idx == keys.length - 1) {
                map.put(keys[idx], val);
            } else {
                @SuppressWarnings("unchecked")
                final var nestedMap = map.containsKey(keys[idx]) && map.get(keys[idx]) instanceof Map
                        ? (Map<String, Object>) map.get(keys[idx])
                        : new HashMap<String, Object>();
                map.put(keys[idx], nestedMap);
                addClaim(nestedMap, Arrays.copyOfRange(keys, idx + 1, keys.length), val);
                break;
            }
        }
    }

    /**
     * The password property name from {@code mongoRealmAuthenticator/prop-password}, so that the
     * denylist covers it even when the deployment renames it — {@value #DEFAULT_PASSWORD_PROPERTY}
     * when that authenticator is absent, disabled, or not resolvable.
     *
     * <p>Shared by every issuance path ({@link JwtTokenManager}, {@link JwtIssuerProvider},
     * {@code accounts}' {@code JwtHelper}): the denylist is only as good as its weakest issuer, so
     * they must all resolve the property the same way rather than each keeping its own copy.
     */
    public static String resolvePasswordProperty(PluginsRegistry registry) {
        if (registry == null) {
            return DEFAULT_PASSWORD_PROPERTY;
        }

        try {
            var pr = registry.getAuthenticator("mongoRealmAuthenticator");

            if (pr != null && pr.isEnabled() && pr.getInstance() instanceof MongoRealmAuthenticator mra) {
                var prop = mra.getPropPassword();

                if (prop != null && !prop.isBlank()) {
                    return prop;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not resolve mongoRealmAuthenticator/prop-password, using default", e);
        }

        return DEFAULT_PASSWORD_PROPERTY;
    }

    /**
     * Builds the signing algorithm from a name and key. Only HMAC algorithms are supported.
     */
    public static Algorithm algorithm(final String name, final String key) {
        if (name == null || key == null) {
            throw new IllegalArgumentException("algorithm and key are required.");
        }

        return switch (name) {
            case "HMAC256", "HS256" -> Algorithm.HMAC256(key.getBytes(StandardCharsets.UTF_8));
            case "HMAC384", "HS384" -> Algorithm.HMAC384(key.getBytes(StandardCharsets.UTF_8));
            case "HMAC512", "HS512" -> Algorithm.HMAC512(key.getBytes(StandardCharsets.UTF_8));
            default -> throw new IllegalArgumentException(
                    "unsupported algorithm for JWT issuance: " + name + " (only HMAC algorithms supported)");
        };
    }
}
