/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
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

import org.bson.BsonDocument;
import org.restheart.ConfigurationException;
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
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * DenyFilterOnUserPasswordPredicate checks if a request has a filter involving
 * the password field
 *
 */
@RegisterPlugin(name = "denyFilterOnUserPwd",
        description = "forbids request with filter on the password property",
        interceptPoint = InterceptPoint.RESPONSE,
        requiresContent = true)
public class DenyFilterOnUserPwd implements MongoInterceptor {
    static final Logger LOGGER = LoggerFactory.getLogger(DenyFilterOnUserPwd.class);

    private boolean enabled = false;
    private String usersDb;
    private String usersCollection;
    private String propNamePassword;

    @Inject("registry")
    PluginsRegistry registry;

    @OnInit
    public void init() {
        PluginRecord<Authenticator> _mra;

        try {
            _mra = registry.getAuthenticator("mongoRealmAuthenticator");
        } catch (ConfigurationException ce) {
            enabled = false;
            return;
        }

        if (_mra == null || !_mra.isEnabled()) {
            enabled = false;
        } else {
            var rhAuth = (MongoRealmAuthenticator) _mra.getInstance();

            this.usersDb = rhAuth.getUsersDb();
            this.usersCollection = rhAuth.getUsersCollection();
            this.propNamePassword = rhAuth.getPropPassword();

            if (usersDb == null
                    || usersCollection == null
                    || propNamePassword == null) {
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
                && request.isGet()
                && this.usersDb.equalsIgnoreCase(request.getDBName())
                && this.usersCollection.equalsIgnoreCase(request.getCollectionName())
                && hasFilterOnPassword(request.getFiltersDocument());
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        response.setInError(HttpStatus.SC_FORBIDDEN, "Using filters on the password property is forbidden");
    }

    private boolean hasFilterOnPassword(BsonDocument filters) {
        if (filters == null || filters.keySet().isEmpty()) {
            return false;
        } else {
            return filters.keySet().contains(propNamePassword) || filters
                .keySet().stream()
                .filter(key -> filters.get(key).isDocument())
                .map(key -> filters.get(key).asDocument())
                .anyMatch(doc -> hasFilterOnPassword(doc));
        }
    }
}
