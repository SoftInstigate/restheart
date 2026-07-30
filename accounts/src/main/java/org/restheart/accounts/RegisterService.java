package org.restheart.accounts;

import com.google.gson.JsonObject;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.emails.EmailSender;
import org.restheart.plugins.accounts.MembershipProvider;
import org.restheart.accounts.util.DbHelper;
import org.restheart.accounts.util.EmailRenderer;
import org.restheart.accounts.util.EmailTemplateLoader;

import org.restheart.accounts.util.Errors;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.accounts.util.TokenUtils;
import org.restheart.exchange.BadRequestException;
import static org.restheart.exchange.ExchangeKeys.META_COLLNAME;
import static org.restheart.exchange.ExchangeKeys._SCHEMAS;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.schema.JsonSchemaNotFoundException;
import org.restheart.plugins.schema.JsonSchemas;
import org.restheart.plugins.schema.SchemaValidationException;
import org.restheart.security.ACLRegistry;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * POST /auth/register
 *
 * <p>Creates a new user with {@code status="pending_verification"} and a new team,
 * then sends an email-verification link (TTL 7 days).
 *
 * <p>Expected request body:
 * <pre>{@code
 * {
 *   "firstName": "Alice",
 *   "lastName":  "Smith",
 *   "teamName":  "Acme Corp",
 *   "email":     "alice@example.com",
 *   "password":  "..."
 * }
 * }</pre>
 *
 * <p>Responses:
 * <ul>
 *   <li>201 — user created, verification email sent (email errors are logged but do not
 *       block the signup response)</li>
 *   <li>400 — missing or invalid body / fields, or the user document violates the
 *       collection's JSON Schema</li>
 *   <li>409 — email address already registered</li>
 *   <li>500 — a JSON Schema is configured on the users collection but cannot be
 *       applied (see below)</li>
 * </ul>
 *
 * <h2>JSON Schema validation</h2>
 *
 * <p>If the users collection carries the {@code jsonSchema} metadata, the user
 * <em>document</em> is validated against that schema before being inserted. Validation is
 * opt-in: no metadata means no validation.
 *
 * <p>The schema speaks the vocabulary of the stored document, not of the request body.
 * The two are mapped as follows:
 *
 * <table>
 *   <caption>request body to user document mapping</caption>
 *   <tr><th>request body</th><th>user document</th></tr>
 *   <tr><td>{@code firstName}</td><td>{@code profile.name}</td></tr>
 *   <tr><td>{@code lastName}</td><td>{@code profile.surname}</td></tr>
 *   <tr><td>{@code email}</td><td>{@code _id}</td></tr>
 *   <tr><td>{@code password}</td><td>{@code password} (hashed)</td></tr>
 *   <tr><td>{@code teamName}</td><td>not stored on the user document — a team document
 *       is created by {@code createInitialTeam}</td></tr>
 * </table>
 *
 * <p>So a schema requiring {@code name} produces {@code #/profile/name: required key
 * [name] not found} for a body that omits {@code firstName}. Violation messages are
 * returned as-is, in the schema's vocabulary.
 *
 * <p>Fields of the request body that are not in the table are dropped before the document
 * is built, so the schema never sees them and cannot reject them — with or without
 * {@code additionalProperties: false}.
 *
 * <p><strong>The document is validated as it is inserted, which is not its final shape.</strong>
 * {@code createInitialTeam} runs after the insert and adds {@code teams} and {@code team}
 * to the user document. A schema with {@code required: ["team"]} therefore always fails,
 * and one with {@code additionalProperties: false} passes here but does not describe the
 * document as it is stored a moment later. Schemas meant for the registration path should
 * declare {@code teams} and {@code team} as optional.
 *
 * <p>A {@code jsonSchema} property that is not a document — including a value explicitly
 * set to {@code null}, e.g. by {@code PATCH} with {@code {"jsonSchema": null}} — means
 * validation is not configured, matching the MongoService pipeline's own checker. Once it
 * is a document, though, validation fails closed: a {@code schemaId} that cannot be
 * resolved from the schema store, or {@code restheart-mongodb} not being deployed, rejects
 * the request with 500 and creates no user, rather than silently skipping validation.
 *
 * <p>The endpoint is public — access is granted via {@code aclRegistry} in {@link #onInit()}.
 */
@RegisterPlugin(
        name             = "registerService",
        description      = "POST /auth/register — public user signup with email verification",
        defaultURI       = "/auth/register",
        enabledByDefault = false)
public class RegisterService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterService.class);

    private static final String JSON_SCHEMAS_PROVIDER = "json-schemas";

    /** keys of the 'jsonSchema' collection metadata; mirror restheart-mongodb's checker */
    private static final String JSON_SCHEMA_PROPERTY = "jsonSchema";
    private static final String SCHEMA_ID_PROPERTY = "schemaId";
    private static final String SCHEMA_STORE_DB_PROPERTY = "schemaStoreDb";

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

    @Inject("registry")
    private PluginsRegistry registry;

    // resolved through the registry rather than with @Inject("json-schemas") on purpose:
    // that provider lives in restheart-mongodb, and a hard injection would disable the
    // whole registration endpoint wherever that module is not deployed. Null here only
    // matters when the users collection actually carries a jsonSchema — see validate().
    private JsonSchemas jsonSchemas;

    @OnInit
    public void onInit() {
        aclRegistry.registerAllow(r -> r.getPath().equals("/auth/register") && (r.isPost() || r.isOptions()));
        aclRegistry.registerAuthenticationRequirement(r -> !r.getPath().equals("/auth/register"));

        this.jsonSchemas = registry.getProviders().stream()
                .filter(pd -> JSON_SCHEMAS_PROVIDER.equals(pd.getName()))
                .filter(pd -> pd.isEnabled())
                .map(pd -> pd.getInstance())
                .filter(p -> JsonSchemas.class.getName().equals(p.rawType().getName()))
                .map(p -> (JsonSchemas) p.get(null))
                .findFirst()
                .orElse(null);

        if (this.jsonSchemas == null) {
            LOGGER.info("Provider '{}' not available: a jsonSchema on the users collection "
                    + "cannot be applied and registration would fail with 500", JSON_SCHEMAS_PROVIDER);
        }
    }

    private DbHelper db(JsonRequest req) {
        return new DbHelper(mclient, RequestOverrides.db(req, conf), RequestOverrides.usersCollection(req, conf));
    }

    private MembershipProvider membership(JsonRequest req) {
        return accountsService.getMembershipProvider(req);
    }

    @Override
    public void handle(JsonRequest req, JsonResponse res) throws Exception {
        if (req.isOptions()) { handleOptions(req); return; }
        if (!req.isPost())   { res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED); return; }

        // ── 1. Parse body ────────────────────────────────────────────────────
        JsonObject body;
        try {
            var raw = req.getContent();
            if (raw == null || !raw.isJsonObject()) {
                Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Request body must be a JSON object");
                return;
            }
            body = raw.getAsJsonObject();
        } catch (BadRequestException e) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Invalid JSON body");
            return;
        }

        // ── 2. Validate required fields ──────────────────────────────────────
        var firstName = extractString(body, "firstName");
        var lastName  = extractString(body, "lastName");
        var teamName  = extractString(body, "teamName");
        var email     = extractString(body, "email");
        var password  = extractString(body, "password");

        if (firstName == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: firstName");
            return;
        }
        if (lastName == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: lastName");
            return;
        }
        if (teamName == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: teamName");
            return;
        }
        if (email == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: email");
            return;
        }
        if (password == null) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST, "Missing required field: password");
            return;
        }

        // ── 3. Check email uniqueness ────────────────────────────────────────
        if (db(req).findUser(email).isPresent()) {
            Errors.error(res, HttpStatus.SC_CONFLICT, "Email already registered");
            return;
        }

        // ── 4. Generate email verification token ─────────────────────────────
        var verificationToken = TokenUtils.generateToken();
        var now               = new BsonDateTime(System.currentTimeMillis());

        // ── 5. Create and insert user (without tenant — MembershipProvider sets it) ──
        // New users start with $unauthenticated role — they can only access
        // public endpoints until email verification assigns the real role.
        // "owner"/"admin"/"member" are team/membership roles, not system ACL roles.
        var rolesArray = new BsonArray();
        rolesArray.add(new BsonString("$unauthenticated"));

        var profile = new BsonDocument()
                .append("name",    new BsonString(firstName))
                .append("surname", new BsonString(lastName));

        var userDoc = new BsonDocument()
                .append("_id",                        new BsonString(email))
                .append("password",                   new BsonString(TokenUtils.hashPassword(password)))
                .append("roles",                      rolesArray)
                .append("profile",                    profile)
                .append("emailVerificationToken",     new BsonString(verificationToken))
                .append("emailVerificationCreatedAt", now);

        // ── 6. Validate user document against collection JSON Schema (if configured) ──
        if (!validate(req, res, userDoc)) {
            return;
        }

        if (!db(req).insertUser(userDoc)) {
            // Concurrent registration or race between findUser and insertUser
            Errors.error(res, HttpStatus.SC_CONFLICT, "Email already registered");
            return;
        }

        // ── 7. Delegate team creation + membership linking to the provider ────
        // note: this adds 'teams' and 'team' to the user document, after it has
        // been validated against the collection's JSON Schema
        var teamRef = membership(req).createInitialTeam(email, teamName);
        var teamId    = teamRef.id().isString()
                ? teamRef.id().asString().getValue()
                : teamRef.id().asObjectId().getValue().toHexString();

        LOGGER.info("User registered: <{}>, team={}", email, teamId);

        // ── 8. Send verification email (best-effort) ─────────────────────────
        try {
            // Check X-Skip-Email header for integration tests
            if ("true".equalsIgnoreCase(req.getHeader("X-Skip-Email"))) {
                LOGGER.debug("Skipping verification email to <{}> (X-Skip-Email header)", email);
            } else {
                var encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
                var verifyLink   = RequestOverrides.frontendUrl(req, conf)
                                   + "/auth/verify"
                                   + "?email=" + encodedEmail
                                   + "&token=" + verificationToken;

                var tmpl = EmailTemplateLoader.loadWithFallback(
                        RequestOverrides.templateVerification(req), conf.verificationTemplatePath(), "verification.html");
                var vars = java.util.Map.of(
                        "app-name", RequestOverrides.appName(req, conf),
                        "year", String.valueOf(java.time.Year.now().getValue()),
                        "first-name", firstName != null ? firstName : "",
                        "email", email,
                        "frontend-url", RequestOverrides.frontendUrl(req, conf),
                        "verification-url", verifyLink);
                var rendered = EmailRenderer.render(tmpl, vars, conf.defaultLocale());
                emails.sendEmail(email, firstName, rendered.subject(), rendered.htmlBody());
            }
        } catch (Exception e) {
            // Log and continue — the user was created; they can request a resend later
            LOGGER.warn("Failed to send verification email to <{}>: {}", email, e.getMessage());
        }

        // ── 9. Respond 201 ───────────────────────────────────────────────────
        res.setStatusCode(HttpStatus.SC_CREATED);
    }

    /**
     * Validates {@code userDoc} against the JSON Schema declared by the users collection.
     *
     * <p>Validation is opt-in — a collection without the {@code jsonSchema} metadata is not
     * validated. Once the metadata is there, though, it is honoured or the request fails:
     * an unresolvable schema is an error, never a reason to insert an unvalidated user.
     *
     * @return {@code true} if the document may be inserted; {@code false} if {@code res}
     *         has been set in error
     */
    private boolean validate(JsonRequest req, JsonResponse res, BsonDocument userDoc) {
        var effectiveDb = RequestOverrides.db(req, conf);
        var effectiveColl = RequestOverrides.usersCollection(req, conf);
        var collProps = META_COLLNAME + "." + effectiveColl;

        BsonDocument propsDoc;

        try {
            propsDoc = mclient.getDatabase(effectiveDb)
                    .getCollection(META_COLLNAME, BsonDocument.class)
                    .find(Filters.eq("_id", collProps))
                    .first();
        } catch (MongoException me) {
            LOGGER.error("Cannot read {}/{} to check for a jsonSchema", effectiveDb, collProps, me);
            Errors.error(res, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Cannot check the users collection for a JSON Schema");
            return false;
        }

        // mirrors JsonSchemaBeforeWriteChecker.resolve(): a 'jsonSchema' property that
        // isn't a document — including a PATCH-ed { "jsonSchema": null } — means
        // validation is not configured, not that the configuration is broken
        if (propsDoc == null
                || !propsDoc.containsKey(JSON_SCHEMA_PROPERTY)
                || !propsDoc.get(JSON_SCHEMA_PROPERTY).isDocument()) {
            return true;
        }

        var meta = propsDoc.getDocument(JSON_SCHEMA_PROPERTY);
        var schemaId = meta.get(SCHEMA_ID_PROPERTY);

        if (schemaId == null) {
            LOGGER.error("Invalid 'jsonSchema' metadata on {}/{}: '{}' is missing",
                    effectiveDb, collProps, SCHEMA_ID_PROPERTY);
            Errors.error(res, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Invalid 'jsonSchema' metadata on the users collection: '"
                    + SCHEMA_ID_PROPERTY + "' is missing");
            return false;
        }

        String schemaStoreDb;

        if (!meta.containsKey(SCHEMA_STORE_DB_PROPERTY)) {
            schemaStoreDb = effectiveDb;
        } else if (meta.get(SCHEMA_STORE_DB_PROPERTY).isString()) {
            schemaStoreDb = meta.getString(SCHEMA_STORE_DB_PROPERTY).getValue();
        } else {
            LOGGER.error("Invalid 'jsonSchema' metadata on {}/{}: '{}' is not a string",
                    effectiveDb, collProps, SCHEMA_STORE_DB_PROPERTY);
            Errors.error(res, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Invalid 'jsonSchema' metadata on the users collection: '"
                    + SCHEMA_STORE_DB_PROPERTY + "' is not a string");
            return false;
        }

        if (jsonSchemas == null) {
            LOGGER.error("{}/{} declares a jsonSchema but the '{}' provider is not available: "
                    + "is restheart-mongodb deployed?", effectiveDb, collProps, JSON_SCHEMAS_PROVIDER);
            Errors.error(res, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Cannot validate the user document: the JSON Schema store is not available");
            return false;
        }

        try {
            jsonSchemas.validate(userDoc, schemaStoreDb, schemaId);
            return true;
        } catch (SchemaValidationException sve) {
            Errors.error(res, HttpStatus.SC_BAD_REQUEST,
                    "User document violates schema: " + String.join(", ", sve.getViolations()));
            return false;
        } catch (JsonSchemaNotFoundException ex) {
            LOGGER.error("{}/{} declares schema {}/{}/{}, which is not in the schema store",
                    effectiveDb, collProps, schemaStoreDb, _SCHEMAS,
                    BsonUtils.getIdAsString(schemaId, false));
            Errors.error(res, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Cannot validate the user document: the JSON Schema declared by the "
                    + "users collection was not found in the schema store");
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a non-blank trimmed string value from a JSON object.
     *
     * @return the trimmed value, or {@code null} if the key is absent, null, or blank
     */
    private static String extractString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        var value = obj.get(key).getAsString().strip();
        return value.isEmpty() ? null : value;
    }
}
