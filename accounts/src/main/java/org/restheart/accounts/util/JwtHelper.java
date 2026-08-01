package org.restheart.accounts.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.security.AuthCookie;
import org.restheart.security.authenticators.MongoRealmAuthenticator;
import org.restheart.security.tokens.JwtConfigProvider;
import org.restheart.security.tokens.JwtIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper (NOT a RESTHeart plugin) to issue RESTHeart-compatible JWTs.
 *
 * <p>RESTHeart verifies JWTs via {@code jwtAuthenticationMechanism}; the expected format is:
 * <pre>
 *   Authorization: Bearer &lt;jwt&gt;
 * </pre>
 * or via the {@code rh_auth=Bearer_&lt;jwt&gt;} cookie (authCookieHandler).
 *
 * <p>Instances are thread-safe: {@link Algorithm} is immutable and {@link JWT} is a static
 * factory.
 *
 * <h2>There is one issuance logic</h2>
 * <p>What ends up in a JWT — which account properties become claims, the denylist, the nested
 * path syntax — is decided by {@link JwtIssuer}, the same class {@code jwtTokenManager} uses on
 * {@code /token}. A RESTHeart JWT is one thing: these are different moments of issuance, not
 * different tokens. This class only supplies the data (the re-read user document, attached
 * params, extra claims) and signs through the issuer.
 *
 * <p>It deliberately does not delegate to the configured {@code TokenManager}: that one is
 * pluggable and need not issue JWTs at all (see {@code RndTokenManager}, which issues opaque
 * tokens), whereas the accounts services require a JWT.
 */
public class JwtHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtHelper.class);

    private final String key;
    private final String issuer;
    private final int ttlMinutes;

    /**
     * Claim names to include in the JWT, read from the request's attached params. Mirrors
     * {@code jwtTokenManager.account-properties-claims}. {@code null} means "no attached-param
     * propagation".
     */
    private final List<String> accountPropertiesClaims;

    /** The issuance logic shared with {@code jwtTokenManager}. */
    private final JwtIssuer jwtIssuer;

    /**
     * Builds a helper without attached-param propagation (backward compatible).
     */
    public JwtHelper(String key, String issuer, int ttlMinutes) {
        this(key, issuer, ttlMinutes, null, null);
    }

    /**
     * Builds a helper supporting {@code account-properties-claims}, without resolving the
     * password property name from {@code mongoRealmAuthenticator} (uses the default
     * {@value JwtIssuer#DEFAULT_PASSWORD_PROPERTY}).
     *
     * @param accountPropertiesClaims names of the attached params to include as JWT claims;
     *                                {@code null} = no additional propagation
     * @deprecated use {@link #JwtHelper(String, String, int, List, PluginsRegistry)} to resolve
     *             the password property name correctly when
     *             {@code mongoRealmAuthenticator/prop-password} is configured to something other
     *             than the default.
     */
    @Deprecated
    public JwtHelper(String key, String issuer, int ttlMinutes, List<String> accountPropertiesClaims) {
        this(key, issuer, ttlMinutes, accountPropertiesClaims, null);
    }

    /**
     * Builds a helper supporting {@code account-properties-claims} and the denylist, resolving
     * the password property name from {@code mongoRealmAuthenticator/prop-password} via
     * {@code registry}.
     *
     * @param accountPropertiesClaims names of the attached params to include as JWT claims;
     *                                {@code null} = no additional propagation
     * @param registry                used to resolve {@code mongoRealmAuthenticator.getPropPassword()};
     *                                {@code null} → uses the default
     */
    public JwtHelper(String key, String issuer, int ttlMinutes, List<String> accountPropertiesClaims,
                     PluginsRegistry registry) {
        this.key = key;
        this.issuer = issuer;
        this.ttlMinutes = ttlMinutes;
        this.accountPropertiesClaims = accountPropertiesClaims;
        this.jwtIssuer = new JwtIssuer(
                Algorithm.HMAC256(key),
                issuer,
                null,
                accountPropertiesClaims,
                resolveRequiredClaims(registry),
                resolvePasswordPropertyName(registry));
    }

    /**
     * The claims that must survive any per-request override, read from the deployment's existing
     * {@code jwtConfigProvider} configuration rather than duplicated under {@code accountsConfig}:
     * they are a property of the JWT this deployment issues, so every issuer must apply the same
     * ones. Without this, a tenant supplying its own claim list on the {@code /auth/*} path could
     * drop a claim later verified on every request and lock itself out.
     */
    private static List<String> resolveRequiredClaims(PluginsRegistry registry) {
        if (registry == null) {
            return null;
        }

        try {
            for (var pr : registry.getProviders()) {
                if ("jwtConfigProvider".equals(pr.getName()) && pr.isEnabled()
                        && pr.getInstance() instanceof JwtConfigProvider jcp
                        && jcp.get(pr) instanceof JwtConfigProvider.JwtConfig cfg) {
                    return cfg.requiredAccountPropertiesClaims();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not resolve jwtConfigProvider required-account-properties-claims", e);
        }

        return null;
    }

    /** Risolve il nome della proprietà password da {@code mongoRealmAuthenticator}, se disponibile. */
    private static String resolvePasswordPropertyName(PluginsRegistry registry) {
        if (registry == null) {
            return JwtIssuer.DEFAULT_PASSWORD_PROPERTY;
        }

        try {
            var pr = registry.getAuthenticator("mongoRealmAuthenticator");
            if (pr != null && pr.isEnabled() && pr.getInstance() instanceof MongoRealmAuthenticator mra) {
                var prop = mra.getPropPassword();
                return prop != null && !prop.isBlank() ? prop : JwtIssuer.DEFAULT_PASSWORD_PROPERTY;
            }
        } catch (ConfigurationException ce) {
            // mongoRealmAuthenticator not configured — fall back to the default
        }

        return JwtIssuer.DEFAULT_PASSWORD_PROPERTY;
    }

    /** Whether {@code claim} must never be copied from the user document into a JWT claim. */
    private boolean isDenylisted(String claim) {
        return jwtIssuer.isDenylisted(claim);
    }

    /**
     * Issues a JWT through {@link JwtIssuer}, the same issuance logic {@code jwtTokenManager}
     * applies, without depending on the configured token manager (which may be absent, or may
     * not issue JWTs at all).
     *
     * <ul>
     *   <li>{@code authDb} — always included when not null/blank (required by
     *       {@code JwtAuthDbVerifier} for multi-team routing)</li>
     *   <li>{@code accountProperties} — filtered by {@code accountPropertiesClaims}: only the
     *       names in the list become claims (e.g. {@code srvNode}, set by
     *       {@code SrvNodeEnricher})</li>
     *   <li>{@code extraClaims} — always included (e.g. {@code team}, {@code status})</li>
     * </ul>
     *
     * @param email             the user identity ({@code sub})
     * @param roles             the roles ({@code roles})
     * @param authDb            the MongoDB authentication database ({@code authDb}); may be {@code null}
     * @param accountProperties all the request's attached params (see {@code Request.attachedParams()});
     *                          filtered by {@code accountPropertiesClaims}; may be {@code null}
     * @param extraClaims       additional claims, always included (e.g. team, status); may be {@code null}
     * @return the signed JWT
     */
    public String issueToken(String email,
                             Set<String> roles,
                             String authDb,
                             Map<String, Object> accountProperties,
                             Map<String, Object> extraClaims,
                             BsonDocument userDocument) {
        return issueToken(email, roles, authDb, accountProperties, extraClaims, userDocument, null);
    }

    /**
     * As {@link #issueToken(String, Set, String, Map, Map, BsonDocument)}, with the effective
     * {@code accountPropertiesClaims} list for this single call (e.g. from
     * {@code RequestOverrides#accountPropertiesClaims}) instead of the one fixed at construction
     * time. The denylist is enforced regardless of which list is in use.
     *
     * @param accountPropertiesClaimsOverride the effective list for this call;
     *                                        {@code null} → use the constructor's
     */
    public String issueToken(String email,
                             Set<String> roles,
                             String authDb,
                             Map<String, Object> accountProperties,
                             Map<String, Object> extraClaims,
                             BsonDocument userDocument,
                             List<String> accountPropertiesClaimsOverride) {
        var effectiveClaims = accountPropertiesClaimsOverride != null
                ? accountPropertiesClaimsOverride
                : accountPropertiesClaims;

        // The properties JwtIssuer selects claims from: the re-read user document, plus the
        // attached params, which win (they are fresher — e.g. srvNode set by SrvNodeEnricher).
        var properties = new java.util.HashMap<String, Object>();

        if (userDocument != null) {
            for (var e : userDocument.entrySet()) {
                properties.put(e.getKey(), bsonValueToObject(e.getValue()));
            }
        }

        if (accountProperties != null) {
            properties.putAll(accountProperties);
        }

        // authDb is always included (as in JwtTokenManager) — JwtAuthDbVerifier needs it
        if (authDb != null && !authDb.isBlank()) {
            properties.put("authDb", authDb);
        }

        // Extra claims are always included (team, status, ...), converted from BsonValue if needed
        var extra = new java.util.HashMap<String, Object>();
        if (extraClaims != null) {
            for (var entry : extraClaims.entrySet()) {
                var val = entry.getValue() instanceof BsonValue bv ? bsonValueToObject(bv) : entry.getValue();
                extra.put(entry.getKey(), val);
            }
        }

        // Claim selection, denylist and nested paths all live in JwtIssuer — the same logic
        // jwtTokenManager applies on /token.
        var builder = jwtIssuer.newBuilder(email, roles, Date.from(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES)))
                .withIssuedAt(Instant.now());

        builder = jwtIssuer.applyAccountClaims(builder, properties, effectiveClaims);

        for (var e : extra.entrySet()) {
            if (isDenylisted(e.getKey())) continue;
            builder = jwtIssuer.withClaim(builder, e.getKey(), e.getValue());
        }

        return jwtIssuer.sign(builder);
    }

    /**
     * Issues a JWT carrying only the explicit claims passed in {@code extraClaims}. Does not
     * include {@code authDb} and does not propagate attached params.
     *
     * @deprecated Use {@link #issueToken(String, Set, String, Map, Map, BsonDocument)} to include
     *             {@code authDb} and the claims configured in {@code account-properties-claims}.
     */
    @Deprecated
    public String issueToken(String email, Set<String> roles, Map<String, String> extraClaims) {
        var builder = jwtIssuer
                .newBuilder(email, roles, Date.from(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES)))
                .withIssuedAt(Instant.now());

        if (extraClaims != null) {
            for (var entry : extraClaims.entrySet()) {
                if (isDenylisted(entry.getKey())) continue;
                builder = jwtIssuer.withClaim(builder, entry.getKey(), entry.getValue());
            }
        }

        return jwtIssuer.sign(builder);
    }

    /**
     * Builds the {@code rh_auth} cookie value compatible with RESTHeart's
     * {@code authCookieHandler}.
     *
     * <p>Format: {@code Bearer_<jwt>}
     *
     * @param jwt the JWT issued by {@link #issueToken}
     * @return the value to assign to the {@code rh_auth} cookie
     */
    public static String cookieValue(String jwt) {
        return AuthCookie.bearerValue(jwt);
    }

    /**
     * Builds the full {@code Set-Cookie} header value in the canonical format
     * {@code <name>=Bearer_<jwt>; Domain=…; Path=/; HttpOnly; SameSite=Strict[; Secure][; Max-Age=…]},
     * compatible with {@code authCookieHandler} (which expects the {@code Bearer_} prefix).
     *
     * <p>This is the single canonical builder of the authentication cookie on the accounts side:
     * every service must go through it (directly or via {@code TokenDelivery}) so that format,
     * {@code Secure} and {@code Max-Age} stay consistent.
     *
     * @param jwt        the JWT
     * @param cookieName the cookie name (e.g. {@code "8x5_auth"})
     * @param domain     the cookie domain (e.g. {@code ".example.com"})
     * @param ttlMinutes the JWT lifetime in minutes — used to set {@code Max-Age};
     *                   when ≤ 0 the cookie is a session cookie (no Max-Age)
     * @param secure     when {@code true} adds the {@code Secure} attribute (required over HTTPS)
     */
    public static String setCookieHeader(String jwt, String cookieName, String domain, int ttlMinutes, boolean secure) {
        long maxAgeSeconds = ttlMinutes > 0 ? (long) ttlMinutes * 60 : -1;
        return AuthCookie.header(cookieName, AuthCookie.bearerValue(jwt), domain, "/",
                secure, true, true, "Strict", maxAgeSeconds);
    }

    /**
     * @deprecated Use {@link #setCookieHeader(String, String, String, int, boolean)} to control
     *             the {@code Secure} attribute explicitly. This overload defaults to {@code Secure}.
     */
    @Deprecated
    public static String setCookieHeader(String jwt, String cookieName, String domain, int ttlMinutes) {
        return setCookieHeader(jwt, cookieName, domain, ttlMinutes, true);
    }

    /**
     * @deprecated Use {@link #setCookieHeader(String, String, String, int, boolean)}: this overload
     *             produces a session cookie (no {@code Max-Age}) and no {@code Secure} attribute.
     */
    @Deprecated
    public static String setCookieHeader(String jwt, String cookieName, String domain) {
        return setCookieHeader(jwt, cookieName, domain, 0, true);
    }

    /**
     * @deprecated Use {@link #setCookieHeader(String, String, String)} with explicit cookie name.
     */
    @Deprecated
    public static String setCookieHeader(String jwt, String domain) {
        return setCookieHeader(jwt, "rh_auth", domain);
    }

    private static Object bsonValueToObject(BsonValue value) {
        return switch (value) {
            case org.bson.BsonString s -> s.getValue();
            case org.bson.BsonBoolean b -> b.getValue();
            case org.bson.BsonInt32 i -> i.getValue();
            case org.bson.BsonInt64 l -> l.getValue();
            case org.bson.BsonDouble d -> d.getValue();
            case org.bson.BsonObjectId oid -> Map.of("$oid", oid.getValue().toHexString());
            case BsonArray a -> {
                var list = new java.util.ArrayList<>();
                for (var item : a) {
                    list.add(bsonValueToObject(item));
                }
                yield list;
            }
            case BsonDocument d -> {
                var map = new java.util.HashMap<String, Object>();
                for (var entry : d.entrySet()) {
                    map.put(entry.getKey(), bsonValueToObject(entry.getValue()));
                }
                yield map;
            }
            default -> value.toString();
        };
    }
}
