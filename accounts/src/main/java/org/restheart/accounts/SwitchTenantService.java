package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import io.undertow.util.HttpString;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
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

import java.util.Map;
import java.util.Set;

/**
 * POST /auth/switch-tenant
 *
 * <p>Switches the caller's active tenant. Verifies membership, issues a new JWT
 * for the target tenant with the correct role, and sets the auth cookie.
 *
 * <p>Request body:
 * <pre>{@code { "tenantId": "def456" }}</pre>
 *
 * <p>Response: 200 with updated cookie. Body:
 * <pre>{@code { "tenant": "def456", "role": "user" }}</pre>
 *
 * <p>Errors:
 * <ul>
 *   <li>400 — missing or invalid body</li>
 *   <li>401 — not authenticated</li>
 *   <li>403 — user does not belong to the requested tenant</li>
 * </ul>
 */
@RegisterPlugin(
        name             = "switchTenantService",
        description      = "POST /auth/switch-tenant — switch active tenant and reissue JWT cookie",
        defaultURI       = "/auth/switch-tenant",
        enabledByDefault = true)
public class SwitchTenantService implements JsonService {

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
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/switch-tenant") && (r.isPost() || r.isOptions()));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) { handleOptions(req); return; }
        if (!req.isPost())   { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        var account = req.getAuthenticatedAccount();
        if (account == null) {
            Errors.error(res, HttpStatus.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

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
        var targetTenantId = jo.get("tenantId").getAsString().trim();
        if (targetTenantId.isBlank()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "tenantId must not be blank");
            return;
        }

        // Load user document
        var email = account.getPrincipal().getName();
        var db    = new DbHelper(mclient, RequestOverrides.db(req, conf));

        var userOpt = db.findUser(email);
        if (userOpt.isEmpty()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "User not found");
            return;
        }
        var userDoc = userOpt.get();

        // Find the role in the requested tenant
        String roleInTenant = null;
        if (userDoc.containsKey("tenants") && userDoc.get("tenants").isArray()) {
            for (var entry : userDoc.getArray("tenants")) {
                if (!entry.isDocument()) continue;
                var e  = entry.asDocument();
                var id = e.containsKey("id") && e.get("id").isString() ? e.getString("id").getValue() : null;
                if (targetTenantId.equals(id)) {
                    roleInTenant = e.containsKey("role") && e.get("role").isString()
                            ? e.getString("role").getValue() : "user";
                    break;
                }
            }
        }

        if (roleInTenant == null) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "User does not belong to this tenant");
            return;
        }

        // Verify user is active
        var status = userDoc.containsKey("status") && userDoc.get("status").isString()
                ? userDoc.getString("status").getValue() : "active";
        if (!"active".equals(status)) {
            Errors.error(res, HttpStatus.SC_FORBIDDEN, "Account is not active");
            return;
        }

        // Issue new JWT for the target tenant
        var token = jwt.issueToken(
                email,
                Set.of(roleInTenant),
                Map.of("tenant", targetTenantId, "status", status));

        // Set cookie
        var domain       = RequestOverrides.cookieDomain(req, conf);
        var cookieHeader = JwtHelper.setCookieHeader(token, conf.cookieName(), domain, conf.jwtTtl());
        res.getHeaders().add(HttpString.tryFromString("Set-Cookie"), cookieHeader);

        // Response body
        var responseBody = new JsonObject();
        responseBody.addProperty("tenant", targetTenantId);
        responseBody.addProperty("role",   roleInTenant);
        res.setContent(responseBody);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
