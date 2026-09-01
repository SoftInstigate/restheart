package org.restheart.accounts;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.ServiceRequest;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.accounts.util.RequestOverrides;
import org.restheart.security.ACLRegistry;
import org.restheart.security.predicates.BsonRequestBlacklistPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Ensures the MongoDB collections and indexes required by restheart-accounts are in place.
 *
 * <p>The target database is read from {@code accountsConfig} (a RESTHeart Provider) so that
 * it matches exactly the database configured for the application (e.g. {@code 8x5}).
 *
 * <p>Collections created if absent:
 * <ul>
 *   <li>{@code oauth_codes} — short-lived OAuth authorization codes (TTL 600 s)</li>
 *   <li>{@code teams}       — team documents with member sub-documents</li>
 * </ul>
 *
 * <p>Indexes are idempotent: MongoDB no-ops when an equivalent index already exists.
 */
@RegisterPlugin(
        name = "accountsInitializer",
        description = "Ensures collections and indexes required by restheart-accounts",
        initPoint = InitPoint.AFTER_STARTUP,
        enabledByDefault = false)
public class AccountsInitializer implements Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountsInitializer.class);

    @Inject("mclient")
    private MongoClient mclient;

    /** RESTHeart risolve automaticamente il Provider e inietta il valore prodotto. */
    @Inject("accountsConfig")
    private AccountsConfigData conf;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    // Every privileged mutation of a user document (roles, teams, team, password,
    // sub, *Token fields, ...) is performed by an accounts-plugin service through
    // DbHelper, i.e. directly via the MongoDB driver — never through the generic
    // MongoDB REST resource, so it never runs through this veto. That means the
    // generic REST resource at /users can safely deny self-service writes to just
    // these fields, for every deployment of restheart-accounts, regardless of
    // whatever ACL the consuming application configures — every other field
    // (profile.*, consents, and any app-level field the tenant adds) is left to
    // the tenant's own ACL.
    private static final BsonRequestBlacklistPredicate USER_WRITE_DENYLIST =
            new BsonRequestBlacklistPredicate(new String[]{
                    "_id", "password", "roles", "team", "teams", "sub",
                    "socialAuths", "providerId",
                    "emailVerificationToken", "emailVerificationCreatedAt",
                    "passwordResetToken", "passwordResetCreatedAt",
                    "inviteToken"});

    /** ACL role carried by a registered-but-not-yet-verified account (see RegisterService). */
    private static final String UNAUTHENTICATED_ROLE = "$unauthenticated";

    // Accounts services that require a *verified* account. A registered-but-unverified
    // user has roles == ["$unauthenticated"] yet is a real, authenticatable principal
    // (Basic auth) that already owns its initial team, so it passes both authentication
    // and every membership-role check ("owner"). These services must not be reachable
    // until the email is verified — enforced centrally by the veto in init().
    //
    // Matched by plugin *name* (isHandledBy), not URL: the defaultURI of each service can
    // be overridden by configuration, but its registered name is fixed — so this stays
    // correct regardless of how a deployment remaps the /auth/* paths.
    //
    // MAINTENANCE: any new accounts service that requires a verified account MUST be added
    // here (the verification gate lives only in this set — there is no per-handler check).
    private static final Set<String> VERIFIED_ONLY_SERVICES = Set.of(
            "changePasswordService",
            "acceptInviteService",
            "listTeamMembersService",
            "listInvitationsService",
            "removeMemberService",
            "getTeamsService",
            "inviteService",
            "teamService",
            "updateProfileService",
            "resendInviteService",
            "updateMemberRoleService",
            "switchTeamService");

    @Override
    public void init() {
        // Generic REST writes to /users are restricted to self-service profile/app-level
        // edits — see USER_WRITE_DENYLIST above for why this is safe to enforce
        // unconditionally.
        aclRegistry.registerVeto(r -> {
            if (!(r instanceof MongoRequest mr)) return false;

            // Match on the *resolved* db/collection (post mongo-mounts), not the request
            // path: the users collection can be reachable through more than one URL —
            // e.g. the conventional /users mount alias AND the raw /{db}/users path if a
            // wildcard mount also exposes it. A path-prefix check on "/users" would miss
            // the second one entirely, letting a client bypass this restriction just by
            // using a different (but equally valid) URL for the same collection.
            if (!"users".equals(mr.getCollectionName())) return false;
            if (!RequestOverrides.db(mr, conf).equals(mr.getDBName())) return false;

            // A tenant that never enabled Sign-up Management never opted into
            // restheart-accounts's opinions on /users — see RequestOverrides.SIGNUP_MGMT_ENABLED.
            // Must be checked here (during authorization) rather than via a post-auth
            // interceptor: vetoes are evaluated as part of authorization, which runs
            // before any REQUEST_AFTER_AUTH interceptor gets a chance to run.
            if (!RequestOverrides.signupMgmtEnabled(mr, conf)) return false;

            // Roles configured via `users-unrestricted-roles` (e.g. an admin console
            // role) bypass this restriction entirely — see AccountsConfigData. Reads
            // the per-team override first (set by e.g. TeamConfigInterceptor), falling
            // back to the node-level YAML config.
            var exemptRoles = RequestOverrides.usersUnrestrictedRoles(mr, conf);
            if (exemptRoles != null && exemptRoles.stream().anyMatch(r::isAccountInRole)) {
                return false;
            }

            var m = r.getMethod();

            // PUT/POST would let a client fully replace or create a user document
            // via generic REST — bypassing every accounts-plugin flow (register,
            // verify, invite, reset-password, switch-team, oauth). Only the
            // dedicated /auth/* endpoints may create/replace user documents.
            if (m == METHOD.PUT || m == METHOD.POST) {
                LOGGER.warn("[accounts] vetoed {} to /users: use the accounts plugin's own endpoints "
                        + "to create or replace user documents", m);
                return true;
            }

            if (m != METHOD.PATCH) return false;

            // Covers dot notation (team._id) and update operators ($set, $push, ...)
            // alike — see BsonRequestBlacklistPredicate.
            if (!USER_WRITE_DENYLIST.resolve(mr.getExchange())) {
                LOGGER.warn("[accounts] vetoed PATCH to /users: the request touches a "
                        + "field reserved to the accounts plugin's own endpoints");
                return true;
            }
            return false;
        });

        // Verification gate: a registered-but-unverified account (role $unauthenticated,
        // authenticated via Basic auth) must not reach the verified-only accounts services.
        // Those services register their ACL allow-rules on path+method only — never on role —
        // so without this veto such an account would pass authorization and every
        // membership-role check (it genuinely is its team's "owner"), letting it act on the
        // platform before confirming its email. Public flows (register, verify, activate,
        // forgot/reset-password, invitation lookup, OAuth) are absent from VERIFIED_ONLY_SERVICES
        // and remain reachable.
        aclRegistry.registerVeto(r -> {
            if (!(r instanceof ServiceRequest<?> sr)) return false;
            if (!sr.isAuthenticated()) return false;                    // anonymous: handled by auth requirements
            if (!sr.isAccountInRole(UNAUTHENTICATED_ROLE)) return false; // verified account: unrestricted
            if (sr.getMethod() == METHOD.OPTIONS) return false;         // never block CORS preflight
            return VERIFIED_ONLY_SERVICES.stream().anyMatch(sr::isHandledBy);
        });

        var database = mclient.getDatabase(conf.db());
        var existing = new HashSet<String>();
        database.listCollectionNames().forEach(existing::add);

        // ------------------------------------------------------------------ collections
        if (!existing.contains("oauth_codes")) {
            database.createCollection("oauth_codes");
            LOGGER.info("accountsInitializer: created collection `oauth_codes` in db `{}`", conf.db());
        }

        if (!existing.contains("teams")) {
            database.createCollection("teams");
            LOGGER.info("accountsInitializer: created collection `teams` in db `{}`", conf.db());
        }

        // ------------------------------------------------------------------ indexes
        try {
            // users — managed by mongoRealmAuthenticator; we only add auth-flow indexes
            if (existing.contains("users")) {
                var users = database.getCollection("users", Document.class);

                users.createIndex(Indexes.ascending("inviteToken"),
                        new IndexOptions().sparse(true).name("inviteToken_1"));

                users.createIndex(Indexes.ascending("passwordResetToken"),
                        new IndexOptions().sparse(true).name("passwordResetToken_1"));

                users.createIndex(Indexes.ascending("emailVerificationToken"),
                        new IndexOptions().sparse(true).name("emailVerificationToken_1"));

                // Active team is stored as a { _id, role } object (9.6.0+); index the id.
                users.createIndex(Indexes.ascending("team._id"),
                        new IndexOptions().name("team._id_1"));
            }

            // oauth_codes — TTL: codes expire after 600 seconds
            var codes = database.getCollection("oauth_codes", Document.class);
            codes.createIndex(Indexes.ascending("created_at"),
                    new IndexOptions().expireAfter(600L, TimeUnit.SECONDS)
                            .name("created_at_ttl"));
            codes.createIndex(Indexes.ascending("code"),
                    new IndexOptions().unique(true).name("code_1"));

            // teams — fast lookup "which teams is this user a member of?"
            var teams = database.getCollection("teams", Document.class);
            teams.createIndex(Indexes.ascending("members.userId"),
                    new IndexOptions().name("members.userId_1"));

            LOGGER.info("accountsInitializer: indexes ensured in db `{}`", conf.db());
        } catch (Exception e) {
            LOGGER.warn("accountsInitializer: index error (non-fatal)", e);
        }
    }
}
