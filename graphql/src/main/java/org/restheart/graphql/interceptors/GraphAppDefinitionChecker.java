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
/***
package org.restheart.security.plugins.interceptors;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.graphql.GraphQLAppDeserializer;

import static org.restheart.plugins.InterceptPoint.RESPONSE;

import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import static org.restheart.plugins.security.TokenManager.ACCESS_CONTROL_EXPOSE_HEADERS;

import java.util.Map;

import com.mongodb.MongoClient;


@RegisterPlugin(name="graphAppDefinitionChecker",
        description = "checks the GraphQL Application Definition",
        interceptPoint = RESPONSE,
        enabledByDefault = true
)
public class GraphAppDefinitionChecker implements MongoInterceptor {
    private String db = null;
    private String coll = null;
    private MongoClient mclient = null;

    @InjectMongoClient
    public void mc(MongoClient mclient) {
        this.mclient = mclient;
    }

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void conf(Map<String, Object> args) {
        args.get("plugins-args"); //// find a graphql

        this.db = "restheart";
        this.coll = "apps";
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var appDef = response.getDbOperationResult().getNewData();

        try {
            if (GraphQLAppDeserializer.fromBsonDocument(appDef) == null) {
                response.rollback(this.mclient);
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }
        } catch(Throwable t) {
            response.rollback(this.mclient);
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return this.db.equals(request.getDBName())
            && this.coll.equals(request.getCollectionName())
            && response.getDbOperationResult() != null
            && response.getDbOperationResult().getNewData() != null;
    }
}
***/