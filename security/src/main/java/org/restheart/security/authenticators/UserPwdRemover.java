/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

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

    private MongoRealmAuthenticator mra;
    private String usersCollection;
    private String propNamePassword;
    private boolean enabled = false;

    @Inject("registry")
    private PluginsRegistry registry;

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

            if (usersCollection == null
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
        DocumentContext dc = JsonPath.using(Configuration.defaultConfiguration()).parse(response.readContent());

        JsonElement content = dc.json();

        if (content == null || content.isJsonNull()) {
            return;
        }

        if (content.isJsonArray()) {
            // GET collection as array of documents (rep=PJ + 'np' qparam)
            try {
                dc.delete("$.[*].".concat(this.propNamePassword));
            } catch(PathNotFoundException pnfe) {
                //nothing to do
            }
        } else if (content.isJsonObject() && content.getAsJsonObject().keySet().contains("_embedded")) {
            if (content.getAsJsonObject().get("_embedded").isJsonArray()) {
                //  GET collection as a compact HAL document
                try {
                    dc.delete("$._embedded.*.".concat(this.propNamePassword));
                } catch(PathNotFoundException pnfe) {
                    //nothing to do
                }
            } else if (content.getAsJsonObject().get("_embedded").isJsonObject()
                    && content.getAsJsonObject().get("_embedded").getAsJsonObject().keySet().contains("rh:doc")
                    && content.getAsJsonObject().get("_embedded").getAsJsonObject().get("rh:doc").isJsonArray()) {
                //  GET collection as a full HAL document
                try {
                    dc.delete("$._embedded.['rh:doc'].*.".concat(this.propNamePassword));
                } catch(PathNotFoundException pnfe) {
                    //nothing to do
                }
            }
        } else if (content.isJsonObject()) {
            // GET document
            try {
                dc.delete("$.".concat(this.propNamePassword));
            } catch(PathNotFoundException pnfe) {
                //nothing to do
            }
        }

        response.setContent(BsonUtils.parse(content.toString()));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
            && request.isGet()
            && (request.attachedParam("override-users-db")  != null || this.mra.getUsersDb(request).equalsIgnoreCase(request.getDBName())) // if usersdb is overridden then any users collection in any db must be processed
            && this.usersCollection.equalsIgnoreCase(request.getCollectionName())
            && !request.isCollectionSize()
            && !request.isCollectionMeta()
            && response.getContent() != null;
    }
}
