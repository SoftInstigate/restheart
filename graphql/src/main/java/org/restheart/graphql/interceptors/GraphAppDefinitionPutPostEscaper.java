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
package org.restheart.graphql.interceptors;

import org.restheart.ConfigurationException;
import org.restheart.ConfigurationKeys;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.graphql.GraphQLService;

import static org.restheart.plugins.InterceptPoint.RESPONSE;

import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BsonUtils;

import static org.restheart.plugins.ConfigurablePlugin.argValue;

import java.util.Map;


@RegisterPlugin(name="graphAppDefinitionPutPostEscaper",
        description = "unescapes $ prefixed keys in GraphQL application definitions",
        interceptPoint = RESPONSE,
        enabledByDefault = true
)
public class GraphAppDefinitionPutPostEscaper implements MongoInterceptor {
    private String db = GraphQLService.DEFAULT_APP_DEF_DB;
    private String coll = GraphQLService.DEFAULT_APP_DEF_COLLECTION;

    private boolean enabled = false;

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void conf(Map<String, Object> args) {
        try {
            Map<String, Object> pluginsArgs = argValue(args, ConfigurationKeys.PLUGINS_ARGS_KEY);
            Map<String, Object> graphqlArgs = argValue(pluginsArgs, "graphql");

            this.db = argValue(graphqlArgs, "db");
            this.coll = argValue(graphqlArgs, "collection");
        } catch(ConfigurationException ce) {
            // nothing to do, using default values
        }

        this.enabled = true;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        response.setContent(BsonUtils.unescapeKeys(response.getContent()));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
            && this.db.equals(request.getDBName())
            && this.coll.equals(request.getCollectionName())
            && request.isGet()
            && response.getContent() != null;
    }
}