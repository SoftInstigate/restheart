package org.restheart.accounts;

import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.exchange.BadRequestException;
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

/**
 * PATCH/DELETE /auth/team
 *
 * <p>Manages the caller's active team as a whole (as opposed to its members,
 * handled by {@link ListTeamMembersService}, {@link InviteService},
 * {@link RemoveMemberService}, {@link UpdateMemberRoleService}). Both methods
 * share this single URI — RESTHeart binds one service per exact path — and both
 * require the caller to hold the {@code <ownershipRole>} role within their active
 * team.
 *
 * <p><b>PATCH</b> renames/edits the team (name and/or description, partial update):
 * <pre>{@code
 * { "name": "Acme Corp", "description": "Our team workspace" }
 * }</pre>
 *
 * <p><b>DELETE</b> deletes the team, but only if it has no other members. The
 * "no other members" invariant is enforced atomically, server-side, via
 * {@link org.restheart.plugins.accounts.MembershipProvider#deleteTeam}: a
 * client-side pre-check (list members, see only the caller, then delete) would be
 * a race condition against a concurrent invite acceptance. Returns {@code 409} if
 * the team still has other members. On success, the caller's own
 * membership/active-team pointer to the deleted team is cleared — they are left
 * with no active team and must create or be invited into a new one.
 *
 * <p>These endpoints can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "teamService",
        description      = "PATCH/DELETE /auth/team — edit or delete the caller's active team",
        defaultURI       = "/auth/team",
        secure           = true,
        enabledByDefault = false)
public class TeamService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TeamService.class);

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("accountsService")
    private AccountsService accountsService;

    @OnInit
    public void onInit() {
        if (conf.membershipEndpointsEnabled()) {
            aclRegistry.registerAllow(r -> r.getPath().equals("/auth/team")
                    && (r.isPatch() || r.isDelete() || r.isOptions()));
        }
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!conf.membershipEndpointsEnabled()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "Endpoint not available");
            return;
        }

        if (req.isPatch())  { handleUpdate(req, res); return; }
        if (req.isDelete()) { handleDelete(req, res); return; }

        res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
    }

    // -------------------------------------------------------------------------
    // PATCH — rename/edit
    // -------------------------------------------------------------------------

    private void handleUpdate(JsonRequest req, JsonResponse res) {
        var account = req.getAuthenticatedAccount();
        var callerEmail = account.getPrincipal().getName();

        var membershipProvider = accountsService.getMembershipProvider(req);
        var membership = membershipProvider.activeMembership(callerEmail);
        var membershipRole = membership.map(m -> m.role()).orElse(null);
        var ownershipRole = RequestOverrides.ownershipRole(req, conf);
        if (membershipRole == null || !membershipRole.equals(ownershipRole)) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Requires " + ownershipRole + " role");
            return;
        }

        var callerTeam = membership.map(m -> m.teamId()).orElse(null);
        if (callerTeam == null || callerTeam.isNull()) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "No team associated with your account");
            return;
        }

        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
            return;
        }
        var jo = body.getAsJsonObject();

        String name = null;
        if (jo.has("name") && !jo.get("name").isJsonNull()) {
            name = jo.get("name").getAsString().trim();
            if (name.isEmpty()) {
                Errors.error(res, HttpStatus.SC_BAD_REQUEST, "name cannot be empty");
                return;
            }
        }
        String description = null;
        if (jo.has("description") && !jo.get("description").isJsonNull()) {
            description = jo.get("description").getAsString().trim();
        }

        if (name == null && description == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "At least one of name or description is required");
            return;
        }

        membershipProvider.updateTeam(callerTeam, name, description);

        LOGGER.info("Team {} updated by <{}>", callerTeam, callerEmail);
        res.setStatusCode(HttpStatus.SC_OK);
    }

    // -------------------------------------------------------------------------
    // DELETE — delete if empty
    // -------------------------------------------------------------------------

    private void handleDelete(JsonRequest req, JsonResponse res) {
        var account = req.getAuthenticatedAccount();
        var callerEmail = account.getPrincipal().getName();

        var membershipProvider = accountsService.getMembershipProvider(req);
        var membership = membershipProvider.activeMembership(callerEmail);
        var membershipRole = membership.map(m -> m.role()).orElse(null);
        var ownershipRole = RequestOverrides.ownershipRole(req, conf);
        if (membershipRole == null || !membershipRole.equals(ownershipRole)) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Requires " + ownershipRole + " role");
            return;
        }

        var callerTeam = membership.map(m -> m.teamId()).orElse(null);
        if (callerTeam == null || callerTeam.isNull()) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "No team associated with your account");
            return;
        }

        boolean deleted;
        try {
            deleted = membershipProvider.deleteTeam(callerEmail, callerTeam);
        } catch (BadRequestException e) {
            Errors.error(res, e);
            return;
        }
        if (!deleted) {
            if (!membershipProvider.isMember(callerEmail, callerTeam)) {
                // Already deleted by a concurrent/duplicate request — since "no other members"
                // means the caller is the only one who could have deleted it.
                Errors.error(res, HttpStatus.SC_NOT_FOUND, "Team not found");
                return;
            }
            Errors.error(res, HttpStatus.SC_CONFLICT, "Team still has other members");
            return;
        }

        LOGGER.info("Team {} deleted by <{}>", callerTeam, callerEmail);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
