package org.restheart.accounts.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper (NON un plugin RESTHeart) per emettere JWT compatibili con RESTHeart.
 *
 * <p>RESTHeart verifica i JWT tramite {@code jwtAuthenticationMechanism}; il formato atteso è:
 * <pre>
 *   Authorization: Bearer &lt;jwt&gt;
 * </pre>
 * oppure via cookie {@code rh_auth=Bearer_&lt;jwt&gt;} (authCookieHandler).
 *
 * <p>Le istanze sono thread-safe: {@link Algorithm} è immutabile e {@link JWT} è una
 * factory statica.
 */
public class JwtHelper {

    private final String key;
    private final String issuer;
    private final int ttlMinutes;

    /**
     * Elenco di nomi di claim da includere nel JWT leggendoli dagli attached-params della
     * request. Replica {@code jwtTokenManager.account-properties-claims}.
     * {@code null} significa "nessuna propagazione di attached-params".
     */
    private final List<String> accountPropertiesClaims;

    /**
     * Costruisce un helper senza propagazione di attached-params (backward-compat).
     */
    public JwtHelper(String key, String issuer, int ttlMinutes) {
        this(key, issuer, ttlMinutes, null);
    }

    /**
     * Costruisce un helper con supporto a {@code account-properties-claims}.
     *
     * @param accountPropertiesClaims nomi degli attached-params da includere come claim JWT;
     *                                {@code null} = nessuna propagazione aggiuntiva
     */
    public JwtHelper(String key, String issuer, int ttlMinutes, List<String> accountPropertiesClaims) {
        this.key = key;
        this.issuer = issuer;
        this.ttlMinutes = ttlMinutes;
        this.accountPropertiesClaims = accountPropertiesClaims;
    }

    /**
     * Emette un JWT replicando la logica di {@code JwtTokenManager}, senza dipendere
     * dal plugin (che potrebbe non essere configurato o potrebbe essere sostituito).
     *
     * <ul>
     *   <li>{@code authDb} — sempre incluso se non nullo/blank (richiesto da
     *       {@code JwtAuthDbVerifier} per il routing multi-tenant)</li>
     *   <li>{@code accountProperties} — filtrato da {@code accountPropertiesClaims}:
     *       solo i nomi presenti nella lista sono aggiunti come claim
     *       (es. {@code srvNode} impostato da {@code SrvNodeEnricher})</li>
     *   <li>{@code extraClaims} — sempre inclusi (es. {@code tenant}, {@code status})</li>
     * </ul>
     *
     * @param email             identità dell'utente ({@code sub})
     * @param roles             ruoli ({@code roles})
     * @param authDb            database MongoDB di autenticazione ({@code authDb}); può essere {@code null}
     * @param accountProperties tutti gli attached-params della request (vedi {@code Request.attachedParams()});
     *                          filtrati da {@code accountPropertiesClaims}; può essere {@code null}
     * @param extraClaims       claim aggiuntivi sempre inclusi (es. tenant, status); può essere {@code null}
     * @return JWT firmato
     */
    public String issueToken(String email,
                             Set<String> roles,
                             String authDb,
                             Map<String, Object> accountProperties,
                             Map<String, String> extraClaims) {
        var algo = Algorithm.HMAC256(key);

        var builder = JWT.create()
                .withSubject(email)
                .withIssuer(issuer)
                .withExpiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .withArrayClaim("roles", roles.toArray(new String[0]));

        // authDb è sempre incluso (come in JwtTokenManager) — serve a JwtAuthDbVerifier
        if (authDb != null && !authDb.isBlank()) {
            builder = builder.withClaim("authDb", authDb);
        }

        // Propaga gli attached-params filtrati da accountPropertiesClaims
        if (accountProperties != null && accountPropertiesClaims != null) {
            for (var claim : accountPropertiesClaims) {
                var val = accountProperties.get(claim);
                if (val == null) continue;
                builder = switch (val) {
                    case String s   -> builder.withClaim(claim, s);
                    case Boolean b  -> builder.withClaim(claim, b);
                    case Integer i  -> builder.withClaim(claim, i);
                    case Long l     -> builder.withClaim(claim, l);
                    case Double d   -> builder.withClaim(claim, d);
                    default         -> builder.withClaim(claim, val.toString());
                };
            }
        }

        // Extra claims sempre inclusi (tenant, status, ecc.)
        if (extraClaims != null) {
            for (var entry : extraClaims.entrySet()) {
                builder = builder.withClaim(entry.getKey(), entry.getValue());
            }
        }

        return builder.sign(algo);
    }

    /**
     * Emette un JWT con i soli claim espliciti passati in {@code extraClaims}.
     * Non include {@code authDb} né propaga gli attached-params.
     *
     * @deprecated Usare {@link #issueToken(String, Set, String, Map, Map)} per includere
     *             {@code authDb} e i claim configurati in {@code account-properties-claims}.
     */
    @Deprecated
    public String issueToken(String email, Set<String> roles, Map<String, String> extraClaims) {
        var algo = Algorithm.HMAC256(key);

        var builder = JWT.create()
                .withSubject(email)
                .withIssuer(issuer)
                .withExpiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .withArrayClaim("roles", roles.toArray(new String[0]));

        if (extraClaims != null) {
            for (var entry : extraClaims.entrySet()) {
                builder = builder.withClaim(entry.getKey(), entry.getValue());
            }
        }

        return builder.sign(algo);
    }

    /**
     * Costruisce il valore del cookie {@code rh_auth} compatibile con
     * {@code authCookieHandler} di RESTHeart.
     *
     * <p>Formato: {@code Bearer_<jwt>}
     *
     * @param jwt token JWT emesso da {@link #issueToken}
     * @return valore da assegnare al cookie {@code rh_auth}
     */
    public static String cookieValue(String jwt) {
        return "Bearer_" + jwt;
    }

    /**
     * Costruisce il valore completo dell'header {@code Set-Cookie} con nome configurabile
     * e {@code Max-Age} coerente con il TTL del JWT.
     *
     * @param jwt        token JWT
     * @param cookieName nome del cookie (es. {@code "8x5_auth"})
     * @param domain     dominio del cookie (es. {@code ".example.com"})
     * @param ttlMinutes durata del JWT in minuti — usata per impostare {@code Max-Age};
     *                   se ≤ 0 il cookie è una session cookie (nessun Max-Age)
     */
    public static String setCookieHeader(String jwt, String cookieName, String domain, int ttlMinutes) {
        var base = cookieName + "=Bearer_" + jwt
                + "; Domain=" + domain
                + "; Path=/; HttpOnly; SameSite=Strict";
        return ttlMinutes > 0 ? base + "; Max-Age=" + ((long) ttlMinutes * 60) : base;
    }

    /**
     * @deprecated Use {@link #setCookieHeader(String, String, String, int)} to set a
     *             persistent cookie with {@code Max-Age} aligned to the JWT TTL.
     */
    @Deprecated
    public static String setCookieHeader(String jwt, String cookieName, String domain) {
        return setCookieHeader(jwt, cookieName, domain, 0);
    }

    /**
     * @deprecated Use {@link #setCookieHeader(String, String, String)} with explicit cookie name.
     */
    @Deprecated
    public static String setCookieHeader(String jwt, String domain) {
        return setCookieHeader(jwt, "rh_auth", domain);
    }
}
