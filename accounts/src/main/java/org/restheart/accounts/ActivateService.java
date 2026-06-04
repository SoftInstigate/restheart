package org.restheart.accounts;

import com.mongodb.client.MongoClient;
import io.undertow.util.Headers;
import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.JwtHelper;
import org.restheart.accounts.util.TokenUtils;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * PATCH /auth/activate
 *
 * <p>Activates a pending invitation: the user supplies their email address,
 * the {@code inviteToken} from the link, a new password, and consent flags.
 * On success the account transitions to {@code status="active"}, the invite
 * token is removed, and a JWT + {@code Set-Cookie} header are returned so
 * the client is immediately logged in.
 *
 * <p>Accessible to unauthenticated users (the user has only a one-time link,
 * no credentials yet).
 *
 * <p>Expected body:
 * <pre>{@code
 * {
 *   "email":    "user@example.com",
 *   "token":    "64-char hex invite token",
 *   "password": "new password",
 *   "consents": { "terms": true, "privacy": true }
 * }
 * }</pre>
 */
@RegisterPlugin(
        name             = "activateService",
        description      = "PATCH /auth/activate — activates an invitation and sets password",
        defaultURI       = "/auth/activate",
        enabledByDefault = true)
public class ActivateService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivateService.class);

    /** Invite token TTL = 7 days expressed in hours. */
    private static final int INVITE_TTL_HOURS = 7 * 24;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;


    private JwtHelper jwt;

    @OnInit
    public void onInit() {
        this.jwt = new JwtHelper(conf.jwtKey(), conf.jwtIssuer(), conf.jwtTtl());
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/activate") && (r.isPatch() || r.isOptions()));
        aclRegistry.registerAuthenticationRequirement(r -> !r.getPath().equals("/auth/activate"));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }
        if (!req.isPatch()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 1. Valida il body
        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
            return;
        }
        var jo = body.getAsJsonObject();

        var email    = stringField(jo, "email");
        var token    = stringField(jo, "token");
        var password = stringField(jo, "password");

        if (email == null || email.isBlank()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "email is required");
            return;
        }
        if (token == null || token.isBlank()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "token is required");
            return;
        }
        if (password == null || password.isBlank()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "password is required");
            return;
        }

        // consents: { "terms": true, "privacy": true }
        if (!jo.has("consents") || !jo.get("consents").isJsonObject()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "consents is required");
            return;
        }
        var consents = jo.getAsJsonObject("consents");
        if (!consents.has("terms") || !consents.get("terms").getAsBoolean()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "consents.terms must be true");
            return;
        }
        if (!consents.has("privacy") || !consents.get("privacy").getAsBoolean()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "consents.privacy must be true");
            return;
        }

        // 2. Cerca l'utente tramite inviteToken
        var userOpt = db(req).findUserByToken("inviteToken", token);
        if (userOpt.isEmpty()) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Invalid or expired invite token");
            return;
        }
        var user = userOpt.get();

        // 3. Verifica corrispondenza email
        var storedEmail = user.containsKey("_id") && user.get("_id").isString()
                ? user.getString("_id").getValue() : null;
        if (!email.trim().toLowerCase().equals(storedEmail)) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Invalid or expired invite token");
            return;
        }

        // 4. Controlla scadenza (TTL 7 giorni)
        if (!user.containsKey("inviteCreatedAt") || !user.get("inviteCreatedAt").isDateTime()) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Invalid or expired invite token");
            return;
        }
        if (TokenUtils.isExpired(user.getDateTime("inviteCreatedAt"), INVITE_TTL_HOURS)) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Invalid or expired invite token");
            return;
        }

        // 5. Verifica che lo status sia "invited"
        var status = user.containsKey("status") && user.get("status").isString()
                ? user.getString("status").getValue() : null;
        if (!"invited".equals(status)) {
            Errors.error(res, HttpStatus.SC_CONFLICT, "Account is not in invited status");
            return;
        }

        // 6. Controlla versioni consensi — la versione accettata viene registrata
        //    dalla configurazione corrente (conf.termsVersion() / conf.privacyVersion()).
        //    Il client indica solo { "terms": true, "privacy": true }, il server
        //    abbina la versione in vigore al momento dell'attivazione.

        var now = new BsonDateTime(System.currentTimeMillis());

        // 7. Leggi l'IP del chiamante
        var ip = extractRemoteIp(req);

        // 8. Costruisci il documento di aggiornamento
        var termsConsent = new BsonDocument();
        termsConsent.put("version",   new BsonString(conf.termsVersion()));
        termsConsent.put("timestamp", now);
        termsConsent.put("ip",        new BsonString(ip));

        var privacyConsent = new BsonDocument();
        privacyConsent.put("version",   new BsonString(conf.privacyVersion()));
        privacyConsent.put("timestamp", now);
        privacyConsent.put("ip",        new BsonString(ip));

        var consentsDoc = new BsonDocument();
        consentsDoc.put("terms",   termsConsent);
        consentsDoc.put("privacy", privacyConsent);

        var setDoc = new BsonDocument();
        setDoc.put("password", new BsonString(TokenUtils.hashPassword(password)));
        setDoc.put("status",   new BsonString("active"));
        setDoc.put("consents", consentsDoc);

        var normalizedEmail = email.trim().toLowerCase();
        db(req).updateUser(normalizedEmail, setDoc);

        // 9. Rimuovi inviteToken e inviteCreatedAt (one-shot)
        db(req).unsetUserFields(normalizedEmail, List.of("inviteToken", "inviteCreatedAt"));

        // 10. Emetti JWT e setta cookie (auto-login)
        var userRoles = new HashSet<String>();
        if (user.containsKey("roles") && user.get("roles").isArray()) {
            for (var v : user.getArray("roles")) {
                if (v.isString()) userRoles.add(v.asString().getValue());
            }
        }

        var extraClaims = new HashMap<String, String>();
        if (user.containsKey("tenant") && user.get("tenant").isString()) {
            extraClaims.put("tenant", user.getString("tenant").getValue());
        }
        extraClaims.put("status", "active");

        var jwtToken = jwt.issueToken(normalizedEmail, userRoles, extraClaims);
        var cookie   = JwtHelper.setCookieHeader(jwtToken, conf.cookieName(), RequestOverrides.cookieDomain(req, conf));
        req.getExchange().getResponseHeaders().add(Headers.SET_COOKIE, cookie);

        // 11. Risponde 200
        var responseBody = new com.google.gson.JsonObject();
        responseBody.addProperty("message", "Account activated");
        res.setContent(responseBody);
        res.setStatusCode(HttpStatus.SC_OK);

        LOGGER.info("Account activated: <{}>", normalizedEmail);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf));
    }

    /**
     * Extracts the remote IP from {@code X-Forwarded-For} (first hop only) or
     * the direct TCP source address.
     */
    private static String extractRemoteIp(JsonRequest req) {
        try {
            var exchange = req.getExchange();
            var xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                return (comma > 0 ? xff.substring(0, comma) : xff).trim();
            }
            var addr = exchange.getSourceAddress();
            return addr != null && addr.getAddress() != null
                    ? addr.getAddress().getHostAddress()
                    : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** Returns the string value of a JSON field, or {@code null} if absent/null. */
    private static String stringField(com.google.gson.JsonObject jo, String field) {
        return jo.has(field) && !jo.get(field).isJsonNull()
                ? jo.get(field).getAsString()
                : null;
    }
}
