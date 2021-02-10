/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.graphql.interceptors;

import org.restheart.ConfigurationKeys;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;

import static org.restheart.plugins.InterceptPoint.REQUEST_AFTER_AUTH;
import static org.restheart.plugins.ConfigurablePlugin.argValue;

import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RegisterPlugin(name="graphAppDefinitionBulkWriteWarner",
        description = "logs a warning message when GraphQL app definitions are created or updated with bulk requests",
        interceptPoint = REQUEST_AFTER_AUTH,
        enabledByDefault = true
)
public class GraphAppDefinitionBulkWriteWarner implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphAppDefinitionBulkWriteWarner.class);

    private String db = null;
    private String coll = null;

    private boolean enabled = false;

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void conf(Map<String, Object> args) {
        Map<String, Object> pluginsArgs = argValue(args, ConfigurationKeys.PLUGINS_ARGS_KEY);
        Map<String, Object> graphqlArgs = argValue(pluginsArgs, "graphql");

        this.db = argValue(graphqlArgs, "db");
        this.coll = argValue(graphqlArgs, "collection");

        this.enabled = true;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        LOGGER.warn("The GraphQL App Definition cannot be checked on bulk patch!");
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
            && this.db.equals(request.getDBName())
            && this.coll.equals(request.getCollectionName())
            && request.getContent() != null
            && (request.isBulkDocuments()
                || (request.isPost() && request.getContent().isArray()));
    }
}