/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.indexes;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteIndexHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of DeleteIndexHandler
     */
    public DeleteIndexHandler() {
        super();
    }

    /**
     * Creates a new instance of DeleteIndexHandler
     *
     * @param next
     */
    public DeleteIndexHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }
        
        String dbName = context.getDBName();
        String collectionName = context.getCollectionName();

        String indexId = context.getIndexId();

        if (indexId.startsWith("_") || indexId.equals("_id_")) {
            ResponseHelper.endExchangeWithMessage(
                    exchange, 
                    context,
                    HttpStatus.SC_UNAUTHORIZED, 
                    indexId + " is a default index and cannot be deleted");
            next(exchange, context);
            return;
        }

        int httpCode = getDatabase().deleteIndex(
                context.getClientSession(),
                dbName, 
                collectionName, 
                indexId);
        
        context.setResponseStatusCode(httpCode);
        
        next(exchange, context);
    }
}
