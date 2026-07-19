package org.restheart.accounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoClient;
import org.bson.BsonValue;
import org.restheart.utils.BsonUtils;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.JwtHelper;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.accounts.util.TokenDelivery;
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

import java.util.Set;
import java.util.stream.Collectors;

/**
 * GET/POST /auth/teams
 *
 * <p>GET returns the list of team memberships for the authenticated user via the
 * active {@link org.restheart.plugins.accounts.MembershipProvider}.
 *
 * <p>Response body example:
 * <pre>{@code
 * [
 *   { "id": "abc123", "name": "Acme Corp", "role": "owner",  "active": true  },
 *   { "id": "def456", "name": "Other Co",  "role": "member", "active": false }
 * ]
 * }</pre>
 *
 * {@code active} marks the team currently encoded in the caller's JWT.
 *
 * <p>POST creates an additional team for the caller (e.g. "New Workspace" the way
 * Slack/Notion let you), assigns them the ownership role, and — since the new team
 * becomes the caller's newly active membership — reissues the auth token, mirroring
 * {@link SwitchTeamService}. Unlike {@code createInitialTeam} (only ever run once,
 * during {@code /auth/register}), this can be called any number of times.
 *
 * <p>Expected POST body:
 * <pre>{@code
 * { "teamName": "Acme Corp" }
 * }</pre>
 *
 * <p>POST response: 201 with updated cookie. Body:
 * <pre>{@code { "id": "64a1b2c3d4e5f6a7b8c9d0e2", "name": "Acme Corp", "role": "owner" }}</pre>
 *
 * <p>Both methods share this single URI — RESTHeart binds one service per exact
 * path, so GET (list) and POST (create) for the {@code /auth/teams} collection
 * must be handled by the same service, matching every other multi-method endpoint
 * in this plugin.
 *
 * <p>These endpoints can be disabled via {@code accountsConfig.membership-endpoints-enabled: false}.
 */
@RegisterPlugin(
        name             = "getTeamsService",
        description      = "GET /auth/teams — list current user's team memberships; POST — create an additional team",
        defaultURI       = "/auth/teams",
        secure           = true,
        enabledByDefault = false)
public class GetTeamsService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetTeamsService.class);

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
        if (conf.membershipEndpointsEnabled()) {
            aclRegistry.registerAllow(r -> r.getPath().equals("/auth/teams")
                    && (r.isGet() || r.isPost() || r.isOptions()));
        }
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        if (req.isOptions()) { handleOptions(req); return; }

        if (!conf.membershipEndpointsEnabled()) {
            Errors.error(res, HttpStatus.SC_NOT_FOUND, "Endpoint not available");
            return;
        }

        if (req.isGet()) { handleList(req, res); return; }
        if (req.isPost()) { handleCreate(req, res); return; }

        res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
    }

    // -------------------------------------------------------------------------
    // GET — list memberships
    // -------------------------------------------------------------------------

    private void handleList(JsonRequest req, JsonResponse res) {
        var account = req.getAuthenticatedAccount();
        var email = account.getPrincipal().getName();

        // Delegate to the MembershipProvider
        var memberships = accountsService.getMembershipProvider(req).listMemberships(email);

        var result = new JsonArray();
        for (var m : memberships) {
            var obj = new JsonObject();
            obj.add("id",            JsonParser.parseString(BsonUtils.toJson(m.teamId())));
            obj.addProperty("name",   m.displayName());
            obj.addProperty("role",   m.role());
            obj.addProperty("active", m.active());
            result.add(obj);
        }

        res.setContent(result);
        res.setStatusCode(HttpStatus.SC_OK);
    }

    // -------------------------------------------------------------------------
    // POST — create an additional team
    // -------------------------------------------------------------------------

    private void handleCreate(JsonRequest req, JsonResponse res) {
        var account = req.getAuthenticatedAccount();
        var email = account.getPrincipal().getName();

        var body = req.getContent();
        if (body == null || !body.isJsonObject()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
            return;
        }
        var jo = body.getAsJsonObject();
        if (!jo.has("teamName") || jo.get("teamName").isJsonNull()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "teamName is required");
            return;
        }
        var teamName = jo.get("teamName").getAsString().trim();
        if (teamName.isEmpty()) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "teamName is required");
            return;
        }

        var teamRef = accountsService.getMembershipProvider(req).createTeam(email, teamName);

        LOGGER.info("Team '{}' ({}) created by <{}>", teamName, teamRef.id(), email);

        // Read system roles from DB — the new team's role is always the ownership role,
        // but system ACL roles are independent of it (see SwitchTeamService).
        var userDoc = new DbHelper(mclient, RequestOverrides.db(req, conf)).findUser(email);
        var dbRoles = userDoc
                .map(u -> u.containsKey("roles") && u.get("roles").isArray()
                        ? u.getArray("roles").stream()
                            .filter(BsonValue::isString)
                            .map(v -> v.asString().getValue())
                            .collect(Collectors.toSet())
                        : Set.<String>of())
                .orElse(Set.of());

        var token = jwt.issueToken(
                email,
                dbRoles,
                RequestOverrides.db(req, conf),
                req.attachedParams(),
                java.util.Map.<String, Object>of(conf.teamClaimName(), teamRef.id()),
                null);

        var delivery = TokenDelivery.resolve(
                req.getQueryParameterOrDefault("delivery", null), TokenDelivery.Mode.COOKIE);

        var responseBody = new JsonObject();
        responseBody.add("id", JsonParser.parseString(BsonUtils.toJson(teamRef.id())));
        responseBody.addProperty("name", teamRef.displayName());
        responseBody.addProperty("role", RequestOverrides.ownershipRole(req, conf));
        if (delivery == TokenDelivery.Mode.BODY) {
            TokenDelivery.body(res, responseBody, conf, token);
        } else {
            TokenDelivery.cookie(res, req, conf, token);
        }
        res.setContent(responseBody);
        res.setStatusCode(HttpStatus.SC_CREATED);
    }
}
