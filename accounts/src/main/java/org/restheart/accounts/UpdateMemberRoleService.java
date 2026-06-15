package org.restheart.accounts;

import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.Errors;
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
 * PATCH /auth/member-role
 *
 * <p>Updates the org-level role of a member within the caller's active tenant.
 * The caller must hold the {@code <ownershipRole>} or {@code admin} role.
 *
 * <p>Accepted roles for the target are the configured {@code member-role-name} or
 * {@code "admin"}. Promoting to the ownership role is not supported via this
 * endpoint (ownership transfer is a separate concern).
 *
 * <p>Expected body:
 * <pre>{@code
 * { "email": "member@example.com", "role": "admin" }
 * }</pre>
 *
 * <p>This endpoint can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "updateMemberRoleService",
        description      = "PATCH /auth/member-role — updates a member's org-level role",
        defaultURI       = "/auth/member-role",
        secure           = true,
        enabledByDefault = false)
public class UpdateMemberRoleService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateMemberRoleService.class);

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("accountsService")
    private AccountsService accountsService;

    @OnInit
    public void onInit() {
        if (conf.membershipEndpointsEnabled()) {
            aclRegistry.registerAllow(r ->
                r.getPath().equals("/auth/member-role") && (r.isPatch() || r.isOptions()));
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

        if (!req.isPatch()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 1. Authenticated caller
        var account = req.getAuthenticatedAccount();
        var callerEmail = account.getPrincipal().getName();

        // 2. Verify caller has owner role in active tenant
        var membershipProvider = accountsService.getMembershipProvider();
        var membership = membershipProvider.activeMembership(callerEmail);
        var membershipRole = membership.map(m -> m.role()).orElse(null);
        var ownershipRole = conf.ownershipRole();
        if (membershipRole == null || !membershipRole.equals(ownershipRole)) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Requires " + ownershipRole + " role");
            return;
        }

        var callerTenant = membership.map(m -> m.tenantId()).orElse(null);
        if (callerTenant == null || callerTenant.isNull()) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "No tenant associated with your account");
            return;
        }

        // 3. Validate body
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
        if (!jo.has("role") || jo.get("role").isJsonNull()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "role is required");
            return;
        }
        var targetEmail = jo.get("email").getAsString().trim().toLowerCase();
        var newRole = jo.get("role").getAsString().trim().toLowerCase();

        // 4. Validate requested role — only memberRoleName or ownershipRole are accepted
        var memberRoleName = conf.memberRoleName();
        if (!memberRoleName.equals(newRole) && !ownershipRole.equals(newRole)) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST,
                "role must be '" + memberRoleName + "' or '" + ownershipRole + "'");
            return;
        }

        // 5. Target must be a member of the caller's tenant
        if (!membershipProvider.isMember(targetEmail, callerTenant)) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "User is not a member of this tenant");
            return;
        }

        // 6. Update role
        membershipProvider.updateMemberRole(targetEmail, callerTenant, newRole);

        LOGGER.info("Role of <{}> in tenant {} updated to '{}' by <{}>",
            targetEmail, callerTenant, newRole, callerEmail);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
