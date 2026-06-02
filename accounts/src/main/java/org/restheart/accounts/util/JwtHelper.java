package org.restheart.accounts.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
     * @param key        chiave HMAC-256 condivisa con RESTHeart ({@code jwtAuthenticationMechanism.key})
     * @param issuer     valore del claim {@code iss}
     * @param ttlMinutes durata del token in minuti
     */
    public JwtHelper(String key, String issuer, int ttlMinutes) {
        this.key = key;
        this.issuer = issuer;
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * Emette un JWT per l'utente specificato.
     *
     * <p>Include le claims standard:
     * <ul>
     *   <li>{@code sub} — email dell'utente</li>
     *   <li>{@code iss} — issuer configurato</li>
     *   <li>{@code exp} — scadenza (now + ttlMinutes)</li>
     *   <li>{@code roles} — array di ruoli (richiesto da mongoRealmAuthenticator)</li>
     * </ul>
     * Più tutti i campi presenti in {@code extraClaims} (es. {@code tenant}, {@code status}).
     *
     * @param email       l'identità dell'utente (claim {@code sub})
     * @param roles       i ruoli (claim {@code roles})
     * @param extraClaims mappa di claims aggiuntivi, può essere {@code null} o vuota
     * @return il JWT firmato come stringa
     */
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
