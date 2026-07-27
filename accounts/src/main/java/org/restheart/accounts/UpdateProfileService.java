package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.RequestOverrides;
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
 * PATCH /auth/profile
 *
 * <p>Self-service update of the caller's own {@code profile.name} /
 * {@code profile.surname} fields (exposed to clients as {@code firstName} /
 * {@code lastName}, matching {@code /auth/register}'s request body).
 *
 * <p>This dedicated endpoint self-registers its own ACL allow rule at startup —
 * same pattern as every other {@code restheart-accounts} endpoint — instead of
 * relying on generic Mongo REST {@code PATCH /users/{email}} plus a
 * tenant-configured ACL allow rule, which isn't guaranteed to exist (the veto
 * installed by {@code AccountsInitializer} only blocks unsafe writes, it doesn't
 * itself grant permission).
 *
 * <p>Expected body (partial updates allowed):
 * <pre>{@code
 * { "firstName": "Alice", "lastName": "Smith" }
 * }</pre>
 */
@RegisterPlugin(
        name             = "updateProfileService",
        description      = "PATCH /auth/profile — self-service profile update",
        defaultURI       = "/auth/profile",
        secure           = true,
        enabledByDefault = false)
public class UpdateProfileService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateProfileService.class);

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @OnInit
    public void onInit() {
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/profile") && (r.isPatch() || r.isOptions()));
    }

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf));
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!req.isPatch()) { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        var account = req.getAuthenticatedAccount();
        var email = account.getPrincipal().getName();

        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
            return;
        }
        var jo = body.getAsJsonObject();

        var updates = new BsonDocument();
        if (jo.has("firstName") && !jo.get("firstName").isJsonNull()) {
            var firstName = jo.get("firstName").getAsString().trim();
            if (firstName.isEmpty()) {
                Errors.error(res, HttpStatus.SC_BAD_REQUEST, "firstName cannot be empty");
                return;
            }
            updates.put("profile.name", new BsonString(firstName));
        }
        if (jo.has("lastName") && !jo.get("lastName").isJsonNull()) {
            var lastName = jo.get("lastName").getAsString().trim();
            if (lastName.isEmpty()) {
                Errors.error(res, HttpStatus.SC_BAD_REQUEST, "lastName cannot be empty");
                return;
            }
            updates.put("profile.surname", new BsonString(lastName));
        }

        if (updates.isEmpty()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "At least one of firstName or lastName is required");
            return;
        }

        db(req).updateUser(email, updates);

        LOGGER.info("Profile updated for <{}>", email);

        var responseBody = new JsonObject();
        responseBody.addProperty("message", "Profile updated successfully");
        res.setContent(responseBody);
        res.setStatusCode(HttpStatus.SC_OK);
    }
}
