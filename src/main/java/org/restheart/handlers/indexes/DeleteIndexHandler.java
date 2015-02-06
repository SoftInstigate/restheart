/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import org.restheart.handlers.PipedHttpHandler;
import org.restheart.utils.HttpStatus;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DeleteIndexHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of DeleteIndexHandler
     */
    public DeleteIndexHandler() {
        super();
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        String dbName = context.getDBName();
        String collectionName = context.getCollectionName();

        String indexId = context.getIndexId();

        if (indexId.startsWith("_") || indexId.equals("_id_")) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_UNAUTHORIZED, indexId + " is a default index and cannot be deleted");
            return;
        }

        int httpCode = getDatabase().deleteIndex(dbName, collectionName, indexId);

        // send the warnings if any (and in case no_content change the return code to ok
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            sendWarnings(httpCode, exchange, context);
        } else {
            exchange.setResponseCode(httpCode);
        }

        exchange.endExchange();
    }
}
