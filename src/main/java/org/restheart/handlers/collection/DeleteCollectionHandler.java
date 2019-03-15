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
package org.restheart.handlers.collection;

import io.undertow.server.HttpServerExchange;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.injectors.LocalCachesSingleton;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteCollectionHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of DeleteCollectionHandler
     */
    public DeleteCollectionHandler() {
        super();
    }

    /**
     * Creates a new instance of DeleteCollectionHandler
     *
     * @param next
     */
    public DeleteCollectionHandler(PipedHttpHandler next) {
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

        OperationResult result = getDatabase().deleteCollection(
                context.getClientSession(),
                context.getDBName(), 
                context.getCollectionName(),
                context.getETag(), 
                context.isETagCheckRequired());

        if (isResponseInConflict(context, result, exchange)) {
            return;
        }

        context.setResponseStatusCode(result.getHttpCode());

        LocalCachesSingleton.getInstance()
                .invalidateCollection(context.getDBName(), context.getCollectionName());

        next(exchange, context);
    }
}
