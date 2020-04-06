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
package org.restheart.mongodb.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.utils.HttpStatus;

/**
 *
 * this handler injects the db properties in the RequestContext this handler is
 * also responsible of sending NOT_FOUND in case of requests involving not
 * existing dbs (that are not PUT)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DbPropsInjector extends PipelinedHandler {
    private final DatabaseImpl dbsDAO = new DatabaseImpl();
    
    /**
     * Creates a new instance of DbPropsInjectorHandler
     *
     */
    public DbPropsInjector() {
        super(null);
    }
    
    /**
     * Creates a new instance of DbPropsInjectorHandler
     *
     * @param next
     */
    public DbPropsInjector(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        
        if (request.isInError()
                || request.isSessions()
                || request.isTxn()
                || request.isTxns()
                || request.isRoot()
                || request.isRootSize()) {
            next(exchange);
            return;
        }

        String dbName = request.getDBName();

        if (dbName != null) {
            BsonDocument dbProps;

            if (!LocalCachesSingleton.isEnabled()
                    || request.getClientSession() != null) {
                dbProps = dbsDAO.getDatabaseProperties(
                        request.getClientSession(), 
                        dbName);
            } else {
                dbProps = LocalCachesSingleton.getInstance()
                        .getDBProperties(dbName);
            }

            // if dbProps is null, the db does not exist
            if (dbProps == null
                    && !(request.isDb()
                    && request.isPut())
                    && !request.isRoot()) {
                BsonResponse.wrap(exchange).setIError(
                                HttpStatus.SC_NOT_FOUND,
                                "Db '" + dbName + "' does not exist");
                next(exchange);
                return;
            }

            if (dbProps != null) {
                dbProps.append("_id", new BsonString(dbName));
            }

            request.setDbProps(dbProps);
        }

        next(exchange);
    }
}
