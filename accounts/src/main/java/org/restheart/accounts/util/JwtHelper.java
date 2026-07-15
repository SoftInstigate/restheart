package org.restheart.accounts.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.security.services.AuthCookie;

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
     *       {@code JwtAuthDbVerifier} per il routing multi-team)</li>
     *   <li>{@code accountProperties} — filtrato da {@code accountPropertiesClaims}:
     *       solo i nomi presenti nella lista sono aggiunti come claim
     *       (es. {@code srvNode} impostato da {@code SrvNodeEnricher})</li>
     *   <li>{@code extraClaims} — sempre inclusi (es. {@code team}, {@code status})</li>
     * </ul>
     *
     * @param email             identità dell'utente ({@code sub})
     * @param roles             ruoli ({@code roles})
     * @param authDb            database MongoDB di autenticazione ({@code authDb}); può essere {@code null}
     * @param accountProperties tutti gli attached-params della request (vedi {@code Request.attachedParams()});
     *                          filtrati da {@code accountPropertiesClaims}; può essere {@code null}
     * @param extraClaims       claim aggiuntivi sempre inclusi (es. team, status); può essere {@code null}
     * @return JWT firmato
     */
    public String issueToken(String email,
                             Set<String> roles,
                             String authDb,
                             Map<String, Object> accountProperties,
                             Map<String, Object> extraClaims,
                             BsonDocument userDocument) {
        var algo = Algorithm.HMAC256(key);

        var builder = JWT.create()
                .withSubject(email)
                .withIssuer(issuer)
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .withArrayClaim("roles", roles.toArray(new String[0]));

        // authDb è sempre incluso (come in JwtTokenManager) — serve a JwtAuthDbVerifier
        if (authDb != null && !authDb.isBlank()) {
            builder = builder.withClaim("authDb", authDb);
        }

        // Merge user document properties into accountProperties
        // Only fields listed in accountPropertiesClaims are included (like JwtTokenManager)
        if (userDocument != null && accountPropertiesClaims != null) {
            if (accountProperties == null) accountProperties = new java.util.HashMap<>();
            for (var claim : accountPropertiesClaims) {
                if (userDocument.containsKey(claim) && !accountProperties.containsKey(claim)) {
                    accountProperties.put(claim, bsonValueToObject(userDocument.get(claim)));
                }
            }
        }

        // Propaga gli attached-params filtrati da accountPropertiesClaims
        if (accountProperties != null && accountPropertiesClaims != null) {
            for (var claim : accountPropertiesClaims) {
                var val = accountProperties.get(claim);
                if (val == null) continue;
                builder = withClaim(builder, claim, val);
            }
        }

        // Extra claims sempre inclusi (team, status, ecc.)
        if (extraClaims != null) {
            for (var entry : extraClaims.entrySet()) {
                // BsonValue viene prima convertito al tipo Java corrispondente
                var val = entry.getValue() instanceof BsonValue bv ? bsonValueToObject(bv) : entry.getValue();
                builder = withClaim(builder, entry.getKey(), val);
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
                .withIssuedAt(Instant.now())
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
        return AuthCookie.bearerValue(jwt);
    }

    /**
     * Costruisce il valore completo dell'header {@code Set-Cookie} nel formato canonico
     * {@code <name>=Bearer_<jwt>; Domain=…; Path=/; HttpOnly; SameSite=Strict[; Secure][; Max-Age=…]},
     * compatibile con {@code authCookieHandler} (che si aspetta il prefisso {@code Bearer_}).
     *
     * <p>Questo è l'unico costruttore canonico del cookie di autenticazione lato accounts:
     * tutti i service devono passare da qui (direttamente o via {@code TokenDelivery}) per
     * garantire coerenza di formato, {@code Secure} e {@code Max-Age}.
     *
     * @param jwt        token JWT
     * @param cookieName nome del cookie (es. {@code "8x5_auth"})
     * @param domain     dominio del cookie (es. {@code ".example.com"})
     * @param ttlMinutes durata del JWT in minuti — usata per impostare {@code Max-Age};
     *                   se ≤ 0 il cookie è una session cookie (nessun Max-Age)
     * @param secure     se {@code true} aggiunge l'attributo {@code Secure} (obbligatorio su HTTPS)
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

    private static JWTCreator.Builder withClaim(JWTCreator.Builder b, String k, Object v) {
        if (k == null || v == null) return b;
        return switch (v) {
            case String s    -> b.withClaim(k, s);
            case Boolean boo -> b.withClaim(k, boo);
            case Integer i   -> b.withClaim(k, i);
            case Long l      -> b.withClaim(k, l);
            case Double d    -> b.withClaim(k, d);
            case Map m       -> { try { yield b.withClaim(k, (Map<String, ?>) m); } catch (ClassCastException e) { yield b; } }
            case List l      -> b.withClaim(k, (List<?>) l);
            default          -> b.withClaim(k, v.toString());
        };
    }

    private static String bsonValueToString(Object v) {
        if (v == null) return null;
        if (v instanceof BsonValue bv) {
            if (bv.isString()) return bv.asString().getValue();
            if (bv.isObjectId()) return bv.asObjectId().getValue().toHexString();
            return bv.toString();
        }
        return v.toString();
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
