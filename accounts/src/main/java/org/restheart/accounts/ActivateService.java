package org.restheart.accounts;

import com.mongodb.client.MongoClient;
import io.undertow.util.Headers;
import org.bson.BsonArray;
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

/**
 * PATCH /auth/activate
 *
 * <p>Activates a pending invitation: the user supplies their email address,
 * the {@code inviteToken} from the link and a new password.
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
 *   "password": "new password"
 * }
 * }</pre>
 */
@RegisterPlugin(
        name             = "activateService",
        description      = "PATCH /auth/activate — activates an invitation and sets password",
        defaultURI       = "/auth/activate",
        enabledByDefault = false)
public class ActivateService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivateService.class);

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("accountsService")
    private AccountsService accountsService;

    private JwtHelper jwt;

    @OnInit
    public void onInit() {
        this.jwt = new JwtHelper(conf.jwtKey(), conf.jwtIssuer(), conf.jwtTtl(), conf.accountPropertiesClaims());
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

        // 1. Validate body
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

        var normalizedEmail = email.trim().toLowerCase();

        // 2. Find invitation by (email, token) in auth_invitations
        var inviteOpt = db(req).findInvitationByEmailAndToken(normalizedEmail, token);
        if (inviteOpt.isEmpty()) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Invalid or expired invite token");
            return;
        }
        var invite = inviteOpt.get();

        // 3. Verify this invitation is for a new user (existing users use /auth/accept-invite)
        var isNewUser = invite.containsKey("isNewUser") && invite.getBoolean("isNewUser").getValue();
        if (!isNewUser) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Please log in to accept this invitation");
            return;
        }

        // 4. Find the user document
        var userOpt = db(req).findUser(normalizedEmail);
        if (userOpt.isEmpty()) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Invalid or expired invite token");
            return;
        }
        var user = userOpt.get();

        var setDoc = new BsonDocument();
        setDoc.put("password", new BsonString(TokenUtils.hashPassword(password)));

        // Assign system ACL role (user is now activated)
        var effectiveRole = RequestOverrides.defaultRole(req, conf);
        var rolesArray = new BsonArray();
        rolesArray.add(new BsonString(effectiveRole));
        setDoc.put("roles", rolesArray);

        db(req).updateUser(normalizedEmail, setDoc);

        // 9. Add org membership now that the user has activated (invitation accepted)
        var orgId    = invite.get("orgId");
        var orgRole  = invite.getString("role").getValue();
        accountsService.getMembershipProvider().addMember(normalizedEmail, orgId, orgRole);

        // 10. Delete the invitation from auth_invitations (one-shot token)
        db(req).deleteInvitation(invite.getObjectId("_id"));

        // 11. Issue JWT and set cookie (auto-login)
        var userRoles = new HashSet<String>();
        userRoles.add(effectiveRole);

        var extraClaims = new HashMap<String, Object>();
        extraClaims.put(conf.tenantClaimName(), orgId);

        var jwtToken = jwt.issueToken(normalizedEmail, userRoles,
                RequestOverrides.db(req, conf),
                req.attachedParams(),
                extraClaims,
                user);
        var cookie   = JwtHelper.setCookieHeader(jwtToken, conf.cookieName(), RequestOverrides.cookieDomain(req, conf));
        req.getExchange().getResponseHeaders().add(Headers.SET_COOKIE, cookie);

        // 11. Respond 200
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
