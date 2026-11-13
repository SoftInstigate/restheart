/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.security.authenticators;

import java.util.Arrays;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refuses, with 403 Forbidden, a request on the users collection of
 * {@code mongoRealmAuthenticator} whose {@code filter} or {@code sort} refers to the password
 * property.
 *
 * <p>The password is never returned, but a condition on it would still tell, request by request,
 * something about the stored hash: a read by the documents it returns, a bulk {@code PATCH} or
 * {@code DELETE} by the number of documents it matches. The check applies to every method, and
 * runs before the request is executed, so a refused write changes nothing.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "denyFilterOnUserPwd",
        description = "forbids requests with a filter or a sort on the password property",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class DenyFilterOnUserPwd implements MongoInterceptor {
    static final Logger LOGGER = LoggerFactory.getLogger(DenyFilterOnUserPwd.class);

    private MongoRealmAuthenticator mra = null;
    private boolean enabled = false;
    private String usersCollection;
    private String propNamePassword;

    @Inject("registry")
    PluginsRegistry registry;

    @OnInit
    public void init() {
        PluginRecord<Authenticator> pr;

        try {
            pr = registry.getAuthenticator("mongoRealmAuthenticator");
        } catch (ConfigurationException ce) {
            enabled = false;
            return;
        }

        if (pr == null || !pr.isEnabled()) {
            enabled = false;
        } else {
            this.mra = (MongoRealmAuthenticator) pr.getInstance();

            this.usersCollection = this.mra.getUsersCollection();
            this.propNamePassword = this.mra.getPropPassword();

            if (usersCollection == null || propNamePassword == null) {
                LOGGER.error("Wrong configuration of mongoRealmAuthenticator! "
                        + "Requests with filters on the password property "
                        + "are not blocked!");
                enabled = false;
            } else {
                enabled = true;
            }
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
                && (request.attachedParam("override-users-db") != null || this.mra.getUsersDb(request).equalsIgnoreCase(request.getDBName())) // if usersdb is overridden then any users collection in any db must be processed
                && this.usersCollection.equalsIgnoreCase(request.getCollectionName())
                && (filtersOnPassword(request) || sortsByPassword(request));
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        response.setInError(HttpStatus.SC_FORBIDDEN, "Using filters on the password property is forbidden");
    }

    /**
     * @return true if a filter query parameter refers to the password property
     */
    private boolean filtersOnPassword(MongoRequest request) {
        var filter = request.getFilter();

        if (filter == null || filter.isEmpty()) {
            return false;
        }

        return filter.stream().anyMatch(f -> {
            try {
                return refersToPassword(BsonUtils.parse(f));
            } catch (Throwable t) {
                // not JSON: the request is never executed, its handler answers 400
                return false;
            }
        });
    }

    /**
     * @return true if {@code value} has a key that is the password property, or a path under it,
     *         or a string that refers to it in an expression ({@code "$password"}), at any depth,
     *         in documents and in arrays ({@code $or}, {@code $and}, {@code $nor})
     */
    private boolean refersToPassword(BsonValue value) {
        if (value == null) {
            return false;
        }

        if (value.isDocument()) {
            BsonDocument doc = value.asDocument();
            return doc.keySet().stream().anyMatch(this::isPassword)
                    || doc.values().stream().anyMatch(this::refersToPassword);
        } else if (value.isArray()) {
            return value.asArray().stream().anyMatch(this::refersToPassword);
        } else if (value.isString()) {
            var s = value.asString().getValue();
            return s.startsWith("$") && isPassword(s.substring(1));
        }

        return false;
    }

    /**
     * @return true if the sort or sort_by query parameter refers to the password property
     */
    private boolean sortsByPassword(MongoRequest request) {
        var sortBy = request.getSortBy();

        if (sortBy == null || sortBy.isEmpty()) {
            return false;
        }

        return sortBy.stream().anyMatch(p -> {
            try {
                return refersToPassword(BsonUtils.parse(p));
            } catch (Throwable t) {
                // not JSON, e.g. "password" or "-password": field names, comma separated
                return Arrays.stream(p.split(",")).map(String::strip)
                        .map(name -> name.startsWith("-") || name.startsWith("+") ? name.substring(1) : name)
                        .anyMatch(this::isPassword);
            }
        });
    }

    private boolean isPassword(String name) {
        return propNamePassword.equals(name) || name.startsWith(propNamePassword + ".");
    }
}
