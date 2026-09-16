/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.mcp;

import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.restheart.configuration.ConfigurationException;
import org.restheart.mongodb.security.ProjectResponse;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.mcp.McpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes the password field from users-collection documents read in-process, the way
 * {@code userPwdRemover} does for a {@code GET} on the REST path.
 *
 * <p>A documents-mode {@code resources/read} never travels the HTTP pipeline (restheart#722), so
 * the RESPONSE interceptor that strips the password never runs for it. This applies the very same
 * rule to what {@link MongoMcpAwareImpl} returns:
 *
 * <ul>
 *   <li>the collection is {@code mongoRealmAuthenticator}'s {@code users-collection}, and</li>
 *   <li>either the database is its {@code users-db}, or the request carries the
 *       {@code override-users-db} attached parameter — in which case a users collection in
 *       <em>any</em> database is processed, exactly as the interceptor does, since a
 *       multi-tenant deployment resolves the users database per request.</li>
 * </ul>
 *
 * <p>The request is the one the authorization decision was evaluated against, which carries the
 * attached parameters of the real {@code /mcp} request. When there is no decision to read it from
 * (an unsecured deployment, or an authorizer that resolves no permissions) the override cannot be
 * known, so the field is hidden in every database's users collection: an unexpected extra field
 * missing from a read is a nuisance, an exposed password is not.
 *
 * <p>The settings are read from the authenticator's plugin record rather than from its class —
 * this module does not depend on {@code restheart-security} — and lazily, on first read, so the
 * lookup does not depend on which plugin initializes first.
 */
final class UsersPasswordHider {
    private static final Logger LOGGER = LoggerFactory.getLogger(UsersPasswordHider.class);

    static final String OVERRIDE_USERS_DB = "override-users-db";

    private record Settings(String usersDb, String usersCollection, String propPassword) {
        static final Settings DISABLED = new Settings(null, null, null);

        boolean enabled() {
            return usersCollection != null && propPassword != null;
        }
    }

    private final PluginsRegistry registry;
    private volatile Settings settings;

    private UsersPasswordHider(PluginsRegistry registry, Settings settings) {
        this.registry = registry;
        this.settings = settings;
    }

    /** Reads the users collection settings from the registered {@code mongoRealmAuthenticator}. */
    static UsersPasswordHider fromRegistry(PluginsRegistry registry) {
        return new UsersPasswordHider(registry, null);
    }

    /** Never hides anything — for tests that exercise no users collection. */
    static UsersPasswordHider disabled() {
        return new UsersPasswordHider(null, Settings.DISABLED);
    }

    /**
     * @param content a document or an array of documents read from {@code db.collection}
     * @return {@code content} with the password field removed when the read targets the users
     *         collection, {@code content} itself otherwise
     */
    BsonValue hide(McpContext ctx, String db, String collection, BsonValue content) {
        var s = settings();

        if (!s.enabled() || content == null || !s.usersCollection().equalsIgnoreCase(collection)) {
            return content;
        }

        var request = ctx != null && ctx.authorization() != null ? ctx.authorization().request() : null;

        if (request != null) {
            var attached = request.attachedParams();
            var overridden = attached != null && attached.get(OVERRIDE_USERS_DB) != null;

            if (!overridden && !s.usersDb().equalsIgnoreCase(db)) {
                return content;
            }
        }

        return ProjectResponse.project(content, new BsonDocument(s.propPassword(), new BsonInt32(0)));
    }

    private Settings settings() {
        var s = settings;
        if (s == null) {
            synchronized (this) {
                s = settings;
                if (s == null) {
                    s = settings = load();
                }
            }
        }
        return s;
    }

    private Settings load() {
        if (registry == null) {
            return Settings.DISABLED;
        }

        try {
            var pr = registry.getAuthenticator("mongoRealmAuthenticator");

            if (pr == null || !pr.isEnabled()) {
                return Settings.DISABLED;
            }

            var args = pr.getConfArgs() == null ? Map.<String, Object>of() : pr.getConfArgs();

            var usersDb = args.get("users-db") instanceof String v ? v : "restheart";
            var usersCollection = args.get("users-collection") instanceof String v ? v : "users";
            var propPassword = args.get("prop-password") instanceof String v ? v : null;

            if (propPassword == null) {
                LOGGER.error("Wrong configuration of mongoRealmAuthenticator! Password stored in users collection are not filtered out from MCP reads");
                return Settings.DISABLED;
            }

            return new Settings(usersDb, usersCollection, propPassword);
        } catch (ConfigurationException ce) {
            // no mongoRealmAuthenticator registered: no users collection to protect
            return Settings.DISABLED;
        }
    }
}
