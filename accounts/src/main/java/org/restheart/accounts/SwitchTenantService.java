package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.undertow.util.HttpString;
import org.bson.BsonValue;
import org.restheart.utils.BsonUtils;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.plugins.accounts.MembershipProvider;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.JwtHelper;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.HttpStatus;

import java.util.Set;

/**
 * POST /auth/switch-tenant
 *
 * <p>Switches the caller's active tenant. Verifies membership via the active
 * {@link MembershipProvider}, issues a new JWT for the target tenant with
 * the correct role, and sets the auth cookie.
 *
 * <p>Request body ({@code tenantId} in MongoDB extended JSON, matching the value from {@code GET /auth/tenants}):
 * <pre>{@code { "tenantId": {"$oid": "64a1b2c3d4e5f6a7b8c9d0e2"} }}</pre>
 *
 * <p>Response: 200 with updated cookie. Body:
 * <pre>{@code { "tenant": {"$oid": "64a1b2c3d4e5f6a7b8c9d0e2"}, "role": "member" }}</pre>
 *
 * <p>Errors:
 * <ul>
 *   <li>400 — missing or invalid body</li>
 *   <li>401 — not authenticated</li>
 *   <li>403 — user does not belong to the requested tenant</li>
 * </ul>
 *
 * <p>This endpoint can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "switchTenantService",
        description      = "POST /auth/switch-tenant \u2014 switch active tenant and reissue JWT cookie",
        defaultURI       = "/auth/switch-tenant",
        secure           = true,
        enabledByDefault = false)
public class SwitchTenantService implements JsonService {

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("accountsService")
    private AccountsService accountsService;

    private JwtHelper jwt;

    @OnInit
    public void onInit() {
        this.jwt = new JwtHelper(conf.jwtKey(), conf.jwtIssuer(), conf.jwtTtl(), conf.accountPropertiesClaims());
        if (conf.membershipEndpointsEnabled()) {
            aclRegistry.registerAllow(r -> r.getPath().equals("/auth/switch-tenant") && (r.isPost() || r.isOptions()));
        }
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!conf.membershipEndpointsEnabled()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "Endpoint not available");
            return;
        }

        if (!req.isPost())   { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        var account = req.getAuthenticatedAccount();

        // Parse body
        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
            return;
        }
        var jo = body.getAsJsonObject();
        if (!jo.has("tenantId") || jo.get("tenantId").isJsonNull()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "tenantId is required");
            return;
        }
        BsonValue targetTenantId;
        try {
            targetTenantId = BsonUtils.parse(jo.get("tenantId").toString());
        } catch (Exception e) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Invalid tenantId format");
            return;
        }
        if (targetTenantId == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "tenantId is required");
            return;
        }

        var email      = account.getPrincipal().getName();
        var membership = accountsService.getMembershipProvider();

        // Find the target membership via the SPI
        var memberships = membership.listMemberships(email);
        org.restheart.plugins.accounts.Membership matched = null;
        for (var m : memberships) {
            if (targetTenantId.equals(m.tenantId())) {
                matched = m;
                break;
            }
        }

        if (matched == null) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "User does not belong to this tenant");
            return;
        }

        // Switch the active membership via the SPI
        try {
            membership.setActiveMembership(email, matched.tenantId());
        } catch (IllegalArgumentException e) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, e.getMessage());
            return;
        }

        // Read system roles from DB — do NOT use matched.role() (membership role)
        var userDoc = new DbHelper(mclient, RequestOverrides.db(req, conf)).findUser(email);
        var dbRoles = userDoc
                .map(u -> u.containsKey("roles") && u.get("roles").isArray()
                        ? u.getArray("roles").stream()
                            .filter(BsonValue::isString)
                            .map(v -> v.asString().getValue())
                            .collect(java.util.stream.Collectors.toSet())
                        : Set.<String>of())
                .orElse(Set.of());

        // Issue new JWT with the system roles from DB, not the membership role
        var token = jwt.issueToken(
                email,
                dbRoles,
                RequestOverrides.db(req, conf),
                req.attachedParams(),
                java.util.Map.<String, Object>of(conf.tenantClaimName(), matched.tenantId()),
                null);

        // Set cookie
        var domain       = RequestOverrides.cookieDomain(req, conf);
        var cookieHeader = JwtHelper.setCookieHeader(token, conf.cookieName(), domain, conf.jwtTtl());
        res.getHeaders().add(HttpString.tryFromString("Set-Cookie"), cookieHeader);

        // Response body
        var responseBody = new JsonObject();
        responseBody.add(conf.tenantClaimName(), JsonParser.parseString(BsonUtils.toJson(matched.tenantId())));
        responseBody.addProperty("role", matched.role());
        res.setContent(responseBody);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
