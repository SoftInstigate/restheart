package org.restheart.accounts;

import com.mongodb.client.MongoClient;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.emails.EmailSender;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.emails.EmailRenderer;
import org.restheart.emails.EmailTemplateLoader;
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
 * <p>Invites a user to the caller's team. Creates the user with
 * {@code roles: ["$unauthenticated"]}, a random password placeholder, and an
 * {@code inviteToken} (256-bit hex, TTL 7 days), then sends the activation email.
 * email.
 *
 * <p>Requires authentication. Callable by membership role: {@code <ownershipRole>} only.
 *
 * <p>Expected body:
 * <pre>{@code
 * { "email": "...", "role": "<memberRoleName>|<ownershipRole>" }
 * }</pre>
 * {@code role} is optional and defaults to the configured {@code member-role-name}.
 *
 * <p>This endpoint can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name = "inviteService",
        description = "POST /auth/invite \u2014 invites a user to the caller's team",
        defaultURI = "/auth/invite",
        secure = true,
        enabledByDefault = false)
public class InviteService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InviteService.class);
    private static final long INVITE_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("emails")
    private EmailSender emails;

    @Inject("accountsService")
    private AccountsService accountsService;

    @OnInit
    public void onInit() {
        if (conf.membershipEndpointsEnabled()) {
            aclRegistry.registerAllow(r -> r.getPath().equals("/auth/invite") && (r.isPost() || r.isOptions()));
        }
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }

        if (!conf.membershipEndpointsEnabled()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "Endpoint not available");
            return;
        }

        if (!req.isPost()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 1. Get authenticated account (enforced by secure=true)
        var account = req.getAuthenticatedAccount();
        var email = account.getPrincipal().getName();

        // 2. Verify membership role: must be ownership-role
        var membership = accountsService.getMembershipProvider(req)
                .activeMembership(email);
        var membershipRole = membership.map(m -> m.role()).orElse(null);
        var ownershipRole = RequestOverrides.ownershipRole(req, conf);
        if (membershipRole == null || !membershipRole.equals(ownershipRole)) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Requires " + ownershipRole + " role");
            return;
        }

        // 3. Read caller's team
        var callerTeam = membership.map(m -> m.teamId()).orElse(null);
        if (callerTeam == null || callerTeam.isNull()) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "No team associated with your account");
            return;
        }

        // 4. Validate body
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

        var defaultRole = conf.memberRoleName();
        var role = defaultRole;
        if (jo.has("role") && !jo.get("role").isJsonNull()) {
            role = jo.get("role").getAsString().trim().toLowerCase();
            if (!defaultRole.equals(role) && !ownershipRole.equals(role)) {
                Errors.error(res, HttpStatus.SC_BAD_REQUEST,
                        "role must be '" + defaultRole + "' or '" + ownershipRole + "'");
                return;
            }
        }

        var membershipProvider = accountsService.getMembershipProvider(req);

        // 5. Check if user already exists and is already in this team
        var existing = db(req).findUser(invitedEmail);
        if (existing.isPresent() && membershipProvider.isMember(invitedEmail, callerTeam)) {
            Errors.error(res, HttpStatus.SC_CONFLICT, "User already in this team");
            return;
        }

        // Check for a pending invitation for the same email+org (covers new users not yet a member)
        if (db(req).findInvitation(invitedEmail, callerTeam).isPresent()) {
            Errors.error(res, HttpStatus.SC_CONFLICT, "Invitation already pending for this user");
            return;
        }

        // 6. Create invite token
        var inviteToken = TokenUtils.generateToken();
        var isNewUser = existing.isEmpty();

        if (isNewUser) {
            // New user: create with $unauthenticated role (no inviteToken on user doc)
            var hashedPwd = TokenUtils.hashPassword(TokenUtils.generateToken());
            var rolesArr = new BsonArray();
            rolesArr.add(new BsonString("$unauthenticated"));

            var userDoc = new BsonDocument();
            userDoc.put("_id", new BsonString(invitedEmail));
            userDoc.put("password", new BsonString(hashedPwd));
            userDoc.put("roles", rolesArr);
            userDoc.put("profile", new BsonDocument());

            if (!db(req).insertUser(userDoc)) {
                Errors.error(res, HttpStatus.SC_CONFLICT, "User already registered");
                return;
            }
        }

        // Always store the invite token in auth_invitations (single source of truth)
        db(req).createInvitation(invitedEmail, inviteToken, callerTeam, role, INVITE_TTL_MS, isNewUser);
        LOGGER.info("Invitation created for {} user <{}> to team={}", isNewUser ? "new" : "existing", invitedEmail, callerTeam);

        // 7. Load team name for the email
        var teamName = loadTeamName(req, email, callerTeam);

        // 9. Send invite email
        //    New users: link to /auth/activate (set password + activate)
        //    Existing users: link to /invitations/accept (accept with current session)
        if (emails != null && emails.isEnabled()) {
            try {
                var encodedEmail = URLEncoder.encode(invitedEmail, StandardCharsets.UTF_8);
                var encodedToken = URLEncoder.encode(inviteToken, StandardCharsets.UTF_8);
                var basePath = isNewUser ? "/auth/activate" : "/invitations/accept";
                var link = RequestOverrides.frontendUrl(req, conf).replaceAll("/$", "")
                        + basePath + "?email=" + encodedEmail + "&token=" + encodedToken;

                var inviterName = account.getPrincipal() != null
                        ? account.getPrincipal().getName()
                        : invitedEmail;

                // Check X-Skip-Email header for integration tests
                if ("true".equalsIgnoreCase(req.getHeader("X-Skip-Email"))) {
                    LOGGER.debug("Skipping invite email to <{}> (X-Skip-Email header)", invitedEmail);
                } else {
                    var tmpl = EmailTemplateLoader.loadWithFallback(
                            RequestOverrides.templateInvite(req), conf.inviteTemplatePath(), "invite.html");
                    var roleDisplay = role.substring(0, 1).toUpperCase() + role.substring(1);
                    var vars = java.util.Map.of(
                            "app-name", RequestOverrides.appName(req, conf),
                            "year", String.valueOf(java.time.Year.now().getValue()),
                            "first-name", inviterName != null ? inviterName : "",
                            "email", invitedEmail,
                            "frontend-url", RequestOverrides.frontendUrl(req, conf),
                            "invite-url", link,
                            "inviter-name", inviterName != null ? inviterName : "",
                            "team-name", teamName != null ? teamName : "",
                            "role", roleDisplay);
                    var rendered = EmailRenderer.render(tmpl, vars, conf.defaultLocale());
                    emails.sendEmailAsync(invitedEmail, invitedEmail, rendered.subject(), rendered.htmlBody());
                }

                LOGGER.info("Invite sent to <{}> by {} (team={}, newUser={})", invitedEmail, inviterName, callerTeam, isNewUser);
            } catch (Exception e) {
                LOGGER.error("Failed to send invite email to <{}>", invitedEmail, e);
            }
        } else {
            LOGGER.warn("email-sender disabled — invite email not sent to <{}>", invitedEmail);
        }

        // 10. Respond 201
        res.setStatusCode(HttpStatus.SC_CREATED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf), RequestOverrides.usersCollection(req, conf));
    }

    /** Loads the team display name for the given teamId, falling back to its extended JSON form. */
    private String loadTeamName(JsonRequest req, String userId, BsonValue teamId) {
        var fallback = teamId.isString()
                ? teamId.asString().getValue()
                : teamId.isObjectId() ? teamId.asObjectId().getValue().toHexString() : teamId.toString();
        try {
            return accountsService.getMembershipProvider(req)
                    .listMemberships(userId)
                    .stream()
                    .filter(m -> m.teamId().equals(teamId))
                    .map(m -> m.displayName())
                    .findFirst()
                    .orElse(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Extracts the active team claim from the authenticated account using the
     * configured claim name, supporting both {@link MongoRealmAccount} and
     * {@link JwtAccount} pipelines.
     */
    private static String extractTeam(io.undertow.security.idm.Account account, String claimName) {
        return switch (account) {
            case MongoRealmAccount mra -> {
                var props = mra.properties();
                if (props == null) yield null;
                var v = props.get(claimName);
                yield v != null && v.isString() ? v.asString().getValue() : null;
            }
            case JwtAccount jwt -> {
                var props = jwt.propertiesAsMap();
                if (props == null) yield null;
                var v = props.get(claimName);
                yield v instanceof String s ? s : null;
            }
            default -> null;
        };
    }
}
