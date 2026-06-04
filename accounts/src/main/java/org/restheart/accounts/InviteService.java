package org.restheart.accounts;

import com.mongodb.client.MongoClient;
import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.email.Ermes;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.accounts.util.EmailTemplates;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.TokenUtils;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.security.JwtAccount;
import org.restheart.security.MongoRealmAccount;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * POST /auth/invite
 *
 * <p>Invites a user to the caller's tenant. Creates the user with
 * {@code status="invited"}, a random password placeholder, and an
 * {@code inviteToken} (256-bit hex, TTL 7 days), then sends the activation
 * email.
 *
 * <p>Requires authentication. Callable by roles: {@code owner}, {@code admin}.
 *
 * <p>Expected body:
 * <pre>{@code
 * { "email": "...", "role": "user|admin" }
 * }</pre>
 * {@code role} is optional and defaults to {@code "user"}.
 */
@RegisterPlugin(
        name             = "inviteService",
        description      = "POST /auth/invite — invites a user to the caller's tenant",
        defaultURI       = "/auth/invite",
        enabledByDefault = true)
public class InviteService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InviteService.class);

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("ermes")
    private Ermes ermes;

    @OnInit
    public void onInit() {
        // Allow all requests to reach the service; auth and role enforcement is done in handle()
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/invite") && (r.isPost() || r.isOptions()));
        aclRegistry.registerAuthenticationRequirement(r -> !r.getPath().equals("/auth/invite"));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }
        if (!req.isPost()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 1. Verifica autenticazione
        var account = req.getAuthenticatedAccount();
        if (account == null) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        // 2. Verifica ruolo: deve essere owner o admin
        var roles = account.getRoles();
        if (!roles.contains("owner") && !roles.contains("admin")) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Requires owner or admin role");
            return;
        }

        // 3. Leggi il tenant del chiamante
        var callerTenant = extractTenant(account);
        if (callerTenant == null || callerTenant.isBlank()) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "No tenant associated with your account");
            return;
        }

        // 4. Valida body
        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
            return;
        }
        var jo = body.getAsJsonObject();

        if (!jo.has("email") || jo.get("email").isJsonNull()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "email is required");
            return;
        }
        var invitedEmail = jo.get("email").getAsString().trim().toLowerCase();

        var role = "user";
        if (jo.has("role") && !jo.get("role").isJsonNull()) {
            role = jo.get("role").getAsString().trim().toLowerCase();
            if (!"user".equals(role) && !"admin".equals(role)) {
                Errors.error(res, HttpStatus.SC_BAD_REQUEST, "role must be 'user' or 'admin'");
                return;
            }
        }

        // 5. Controlla se l'utente esiste già
        var existing = db(req).findUser(invitedEmail);
        if (existing.isPresent()) {
            var existingDoc = existing.get();
            // Check if already in this team
            if (isAlreadyInTenant(existingDoc, callerTenant)) {
                Errors.error(res, HttpStatus.SC_CONFLICT, "User already in this team");
                return;
            }
            // Multi-team: add membership to existing active user
            var membership = new BsonDocument()
                    .append("id",   new BsonString(callerTenant))
                    .append("role", new BsonString(role));
            db(req).addTenantMembership(invitedEmail, membership);
            // Send notification email (best-effort)
            if (ermes != null && ermes.isEnabled()) {
                try {
                    var teamName = loadTeamName(db(req), callerTenant);
                    var inviterName = account.getPrincipal() != null
                            ? account.getPrincipal().getName() : invitedEmail;
                    ermes.sendEmail(
                            invitedEmail,
                            invitedEmail,
                            EmailTemplates.inviteSubject(teamName, conf.appName()),
                            EmailTemplates.inviteBody(teamName, inviterName,
                                    conf.frontendUrl(), conf.appName()));
                    LOGGER.info("Added existing user <{}> to tenant={}", invitedEmail, callerTenant);
                } catch (Exception e) {
                    LOGGER.warn("Failed to send team-added email to <{}>: {}", invitedEmail, e.getMessage());
                }
            }
            res.setStatusCode(HttpStatus.SC_CREATED);
            return;
        }

        // 6. Crea utente con status="invited"
        var inviteToken = TokenUtils.generateToken();
        var now         = new BsonDateTime(System.currentTimeMillis());
        // password casuale hashata — l'utente non può loggarsi finché non attiva l'account
        var hashedPwd   = TokenUtils.hashPassword(TokenUtils.generateToken());

        var rolesArr = new BsonArray();
        rolesArr.add(new BsonString(role));

        var tenantsArr = new BsonArray();
        tenantsArr.add(new BsonDocument()
                .append("id",   new BsonString(callerTenant))
                .append("role", new BsonString(role)));

        var userDoc = new BsonDocument();
        userDoc.put("_id",             new BsonString(invitedEmail));
        userDoc.put("password",        new BsonString(hashedPwd));
        userDoc.put("roles",           rolesArr);
        userDoc.put("status",          new BsonString("invited"));
        userDoc.put("tenant",          new BsonString(callerTenant));
        userDoc.put("tenants",         tenantsArr);
        userDoc.put("profile",         new BsonDocument());
        userDoc.put("inviteToken",     new BsonString(inviteToken));
        userDoc.put("inviteCreatedAt", now);

        if (!db(req).insertUser(userDoc)) {
            // Race condition: un'altra richiesta ha inserito lo stesso utente
            Errors.error(res, HttpStatus.SC_CONFLICT, "User already registered");
            return;
        }

        // 7. Carica il nome del team per l'email
        var teamName = loadTeamName(db(req), callerTenant);

        // 8. Invia email invito
        if (ermes != null && ermes.isEnabled()) {
            try {
                var link = conf.frontendUrl().replaceAll("/$", "")
                        + "/auth/activate"
                        + "?email=" + URLEncoder.encode(invitedEmail, StandardCharsets.UTF_8)
                        + "&token=" + URLEncoder.encode(inviteToken, StandardCharsets.UTF_8);

                var inviterName = account.getPrincipal() != null
                        ? account.getPrincipal().getName()
                        : invitedEmail;

                ermes.sendEmail(
                        invitedEmail,
                        invitedEmail,
                        EmailTemplates.inviteSubject(teamName, conf.appName()),
                        EmailTemplates.inviteBody(teamName, inviterName, link, conf.appName()));

                LOGGER.info("Invite sent to <{}> by {} (tenant={})", invitedEmail, inviterName, callerTenant);
            } catch (Exception e) {
                LOGGER.error("Failed to send invite email to <{}>", invitedEmail, e);
            }
        } else {
            LOGGER.warn("Ermes disabled — invite email not sent to <{}>", invitedEmail);
        }

        // 9. Risponde 201
        res.setStatusCode(HttpStatus.SC_CREATED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf));
    }

    /** Returns true if the user document already has {@code tenantId} in its {@code tenants} array. */
    private static boolean isAlreadyInTenant(BsonDocument userDoc, String tenantId) {
        // Check legacy single-tenant field
        if (userDoc.containsKey("tenant") && userDoc.get("tenant").isString()
                && tenantId.equals(userDoc.getString("tenant").getValue())) {
            return true;
        }
        // Check tenants array
        if (userDoc.containsKey("tenants") && userDoc.get("tenants").isArray()) {
            for (var entry : userDoc.getArray("tenants")) {
                if (entry.isDocument()) {
                    var doc = entry.asDocument();
                    if (doc.containsKey("id") && doc.get("id").isString()
                            && tenantId.equals(doc.getString("id").getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Loads the team display name for the given tenantId, falling back to the raw ID. */
    private static String loadTeamName(DbHelper db, String tenantId) {
        try {
            return db.findTeam(tenantId)
                    .filter(t -> t.containsKey("name") && t.get("name").isString())
                    .map(t -> t.getString("name").getValue())
                    .orElse(tenantId);
        } catch (Exception e) {
            return tenantId;
        }
    }

    /**
     * Extracts the {@code tenant} claim from the authenticated account,
     * supporting both {@link MongoRealmAccount} and {@link JwtAccount} pipelines.
     */
    private static String extractTenant(io.undertow.security.idm.Account account) {
        return switch (account) {
            case MongoRealmAccount mra -> {
                var props = mra.properties();
                if (props == null) yield null;
                var v = props.get("tenant");
                yield v != null && v.isString() ? v.asString().getValue() : null;
            }
            case JwtAccount jwt -> {
                var props = jwt.propertiesAsMap();
                if (props == null) yield null;
                var v = props.get("tenant");
                yield v instanceof String s ? s : null;
            }
            default -> null;
        };
    }
}
