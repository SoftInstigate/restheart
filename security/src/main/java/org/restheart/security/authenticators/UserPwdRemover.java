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

import com.google.gson.JsonElement;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.restheart.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "userPwdRemover",
        description = "filters out the password from the response",
        interceptPoint = InterceptPoint.RESPONSE,
        requiresContent = true)
public class UserPwdRemover implements MongoInterceptor {
    static final Logger LOGGER = LoggerFactory.getLogger(UserPwdRemover.class);

    private String usersDb;
    private String usersCollection;
    private String propNamePassword;
    private boolean enabled = false;

    @InjectPluginsRegistry
    public void init(PluginsRegistry registry) {
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
                        + "Password stored in users collection "
                        + "are not filtered out from the response");
                enabled = false;
            } else {
                enabled = true;
            }
        }
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        DocumentContext dc = JsonPath.parse(response.readContent());

        JsonElement content = dc.json();

        if (content == null || content.isJsonNull()) {
            return;
        }

        if (content.isJsonArray()) {
            // GET collection as array of documents (rep=PJ + 'np' qparam)
            dc.delete("$.[*].".concat(this.propNamePassword));
        } else if (content.isJsonObject()
                && content.getAsJsonObject().keySet().contains("_embedded")) {
            if (content.getAsJsonObject().get("_embedded").isJsonArray()) {
                //  GET collection as a compact HAL document
                dc.delete("$._embedded.*.".concat(this.propNamePassword));
            } else if (content.getAsJsonObject().get("_embedded").isJsonObject()
                    && content.getAsJsonObject()
                            .get("_embedded").getAsJsonObject().keySet().contains("rh:doc")
                    && content.getAsJsonObject()
                            .get("_embedded").getAsJsonObject().get("rh:doc").isJsonArray()) {
                //  GET collection as a full HAL document
                dc.delete("$._embedded.['rh:doc'].*.".concat(this.propNamePassword));
            }

        } else if (content.isJsonObject()) {
            // GET document
            dc.delete("$.".concat(this.propNamePassword));
        }

        response.setContent(BsonUtils.parse(content.toString()));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
                && request.isGet()
                && this.usersDb.equalsIgnoreCase(request.getDBName())
                && this.usersCollection.equalsIgnoreCase(request.getCollectionName())
                && response.getContent() != null;
    }
}
