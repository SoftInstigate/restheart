/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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
package org.restheart.security.interceptors;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.authenticators.MongoRealmAuthenticator;
import org.restheart.security.authorizers.MongoAclAuthorizer;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HttpHeaders;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.jayway.jsonpath.JsonPath;

import io.undertow.attribute.ExchangeAttributes;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name="rootRoleGuard",
        description = "forbids creating or updating mongoRealmAuthenticator accounts with the root-role of the mongoAclAuthorizer",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        enabledByDefault = true,
        priority = 11) // after pwd hasher to avoid logging the pwd
public class RootRoleGuard implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RootRoleGuard.class);

    @Inject("registry")
    PluginsRegistry registry;

    private MongoRealmAuthenticator mra;
    private boolean enabled = true;
    private String rootRole = null;
    private String usersCollection = null;
    private String jsonPathRoles = null;

    @OnInit
    public void init() {
        var _authenticator = registry.getAuthenticators().stream()
            .filter(PluginRecord::isEnabled)
            .map(PluginRecord::getInstance)
            .filter(a -> a instanceof MongoRealmAuthenticator)
            .map(a -> (MongoRealmAuthenticator) a)
            .findFirst();

        var _authorizer = registry.getAuthorizers().stream()
            .filter(PluginRecord::isEnabled)
            .map(PluginRecord::getInstance)
            .filter(a -> a instanceof MongoAclAuthorizer)
            .map(a -> (MongoAclAuthorizer) a)
            .findFirst();

        if (_authorizer.isPresent() && _authenticator.isPresent()) {
            var authorizer = _authorizer.get();
            this.mra = _authenticator.get();
            this.rootRole = authorizer.rootRole();
            this.usersCollection = this.mra.getUsersCollection();
            this.jsonPathRoles = this.mra.getJsonPathRoles();
        } else {
            enabled = false;
        }
    }


    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var _content = request.getContent();

        if (_content instanceof BsonArray array && check(array)) {
            logWarning(request);
            response.setInError(HttpStatus.SC_FORBIDDEN, "Forbidden. The request has been logged.");
        } else if (_content instanceof BsonDocument doc && check(doc)) {
            logWarning(request);
            response.setInError(HttpStatus.SC_FORBIDDEN, "Forbidden. The request has been logged.");
        }
    }

    private void logWarning(MongoRequest request) {
        var db = request.getDBName();
        var clientId = request.isAuthenticated() ? request.getAuthenticatedAccount().getPrincipal().getName() : "unknown";
        var clientRoles = request.isAuthenticated() ? request.getAuthenticatedAccount().getRoles() : "$unauthenticated";
        var remoteIp = ExchangeAttributes.remoteIp().readAttribute(request.getExchange());
        var content = BsonUtils.toJson(request.getContent());
        var xff = request.getHeader(HttpHeaders.X_FORWARDED_FOR);
        LOGGER.warn("{} with roles {} tried to set an account in the collection {}.{} with roles array ({}) containing the root-role ({}). Remote IP={}, X-Forwared-For Header={}, content={}", clientId, clientRoles, db, this.mra.getUsersCollection(), this.jsonPathRoles, this.rootRole, remoteIp, xff, content);
    }

    /**
     *
     * @param array
     * @return true when roles in any document of array contains the root-role
     */
    private boolean check(BsonArray array) {
        return array.stream()
            .filter(el -> el.isDocument())
            .map(el -> el.asDocument())
            .anyMatch(doc -> check(doc));
    }

    /**
     *
     * @param array
     */
    private boolean check(BsonDocument doc) {
        return contains(roles(doc), this.rootRole);
    }

    /**
     *
     * @param doc
     * @return the roles array in doc
     */
    private JsonArray roles(BsonDocument doc) {
        JsonElement account;

        try {
            account = JsonPath.read(doc.toJson(), "$");
            var element = JsonPath.read(account, this.jsonPathRoles);

            return element instanceof JsonArray array ? array: new JsonArray();
        } catch (Throwable t) {
            return new JsonArray();
        }
    }

    private boolean contains(JsonArray array, String srt) {
        return array.contains(new JsonPrimitive(srt));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (request.attachedParam("override-users-db") == null) {
            return enabled && request.isWriteDocument() && request.getDBName().equals(this.mra.getUsersDb()) && request.getCollectionName().equals(this.usersCollection);
        } else {
            // when users db can be overridden, all dbs must be checked
            return enabled && request.isWriteDocument() && request.getCollectionName().equals(this.usersCollection);
        }
    }
}
