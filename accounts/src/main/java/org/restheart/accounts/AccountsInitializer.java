package org.restheart.accounts;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.restheart.accounts.config.AccountsConfigData;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.MongoRequest;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
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
        name             = "accountsInitializer",
        description      = "Ensures collections and indexes required by restheart-accounts",
        initPoint        = InitPoint.AFTER_STARTUP,
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

    @Override
    public void init() {
        // Veto any write to /users that contains the reserved 'sub' field.
        // 'sub' is the JWT claim used in ACL read filters (@user.sub);
        // allowing it to be written would let a user forge their own identity.
        aclRegistry.registerVeto(r -> {
            if (!r.getPath().startsWith("/users")) return false;
            var m = r.getMethod();
            if (m != METHOD.POST && m != METHOD.PUT && m != METHOD.PATCH) return false;
            if (!(r instanceof MongoRequest mr)) return false;
            var content = mr.getContent();
            if (content == null || !content.isDocument()) return false;
            if (content.asDocument().containsKey("sub")) {
                LOGGER.warn("[accounts] vetoed write to /users: 'sub' is a reserved field");
                return true;
            }
            return false;
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

                users.createIndex(Indexes.ascending("status"),
                        new IndexOptions().name("status_1"));

                users.createIndex(Indexes.ascending("tenant"),
                        new IndexOptions().name("tenant_1"));
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
