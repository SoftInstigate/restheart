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
 * DELETE /auth/remove-member
 *
 * <p>Removes a member from the caller's active tenant. The caller must hold the
 * {@code <ownershipRole>} or {@code admin} role within that tenant.
 *
 * <p>Owners cannot remove themselves (to prevent leaving an org without an owner).
 *
 * <p>Expected body:
 * <pre>{@code
 * { "email": "member@example.com" }
 * }</pre>
 *
 * <p>This endpoint can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "removeMemberService",
        description      = "DELETE /auth/remove-member — removes a member from the caller's tenant",
        defaultURI       = "/auth/remove-member",
        secure           = true,
        enabledByDefault = false)
public class RemoveMemberService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveMemberService.class);

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
                r.getPath().equals("/auth/remove-member") && (r.isDelete() || r.isOptions()));
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

        if (!req.isDelete()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 1. Authenticated caller
        var account = req.getAuthenticatedAccount();
        var callerEmail = account.getPrincipal().getName();

        // 2. Verify caller has owner or admin role in active tenant
        var membershipProvider = accountsService.getMembershipProvider();
        var membership = membershipProvider.activeMembership(callerEmail);
        var membershipRole = membership.map(m -> m.role()).orElse(null);
        var ownershipRole = conf.ownershipRole();
        if (membershipRole == null || (!membershipRole.equals(ownershipRole) && !membershipRole.equals("admin"))) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Requires " + ownershipRole + " or admin role");
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
        var targetEmail = jo.get("email").getAsString().trim().toLowerCase();

        // 4. Owner cannot remove themselves
        if (callerEmail.equalsIgnoreCase(targetEmail) && membershipRole.equals(ownershipRole)) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Owner cannot remove themselves from the tenant");
            return;
        }

        // 5. Target must be a member of the caller's tenant
        if (!membershipProvider.isMember(targetEmail, callerTenant)) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "User is not a member of this tenant");
            return;
        }

        // 6. Remove
        membershipProvider.removeMember(targetEmail, callerTenant);

        LOGGER.info("Member <{}> removed from tenant {} by <{}>", targetEmail, callerTenant, callerEmail);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
