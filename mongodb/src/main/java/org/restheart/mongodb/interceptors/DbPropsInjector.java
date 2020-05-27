/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.interceptors;

import com.mongodb.MongoClient;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

/**
 *
 * Injects the db properties into the MongoRequest

 It is also responsible of sending NOT_FOUND in case of requests involving not
 existing dbs (that are not PUT)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "dbPropsInjector",
        description = "Injects the db properties into the BsonRequest",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
        priority = Integer.MIN_VALUE)
public class DbPropsInjector implements MongoInterceptor {
    private DatabaseImpl dbsDAO = null;

    /**
     * Makes sure that dbsDAO is instantiated after MongoClient initialization
     *
     * @param mclient
     */
    @InjectMongoClient
    public void init(MongoClient mclient) {
        this.dbsDAO = new DatabaseImpl();
    }

    /**
     * @param request
     * @param response
     * @throws Exception
     */
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        String dbName = request.getDBName();

        if (dbName != null) {
            BsonDocument dbProps;

            if (!MetadataCachesSingleton.isEnabled() || request.isNoCache()) {
                dbProps = dbsDAO.getDatabaseProperties(
                        request.getClientSession(),
                        dbName);
            } else {
                dbProps = MetadataCachesSingleton.getInstance()
                        .getDBProperties(dbName);
            }

            // if dbProps is null, the db does not exist
            if (dbProps == null
                    && !(request.isDb()
                    && request.isPut())
                    && !request.isRoot()) {
                response.setInError(
                        HttpStatus.SC_NOT_FOUND,
                        "Db '" + dbName + "' does not exist");
                return;
            }

            if (dbProps != null) {
                dbProps.append("_id", new BsonString(dbName));
            }

            request.setDbProps(dbProps);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return this.dbsDAO != null
                && request.isHandledBy("mongo")
                && !(request.isInError()
                || request.isSessions()
                || request.isTxn()
                || request.isTxns()
                || request.isRoot()
                || request.isRootSize()
                || request.isMetrics());
    }
}
