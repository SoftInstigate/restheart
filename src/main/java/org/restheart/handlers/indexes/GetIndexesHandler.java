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
import java.util.List;
import org.bson.BsonDocument;
import org.restheart.representation.Resource;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetIndexesHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of GetIndexesHandler
     */
    public GetIndexesHandler() {
        super();
    }

    /**
     * Creates a new instance of GetIndexesHandler
     *
     * @param next
     */
    public GetIndexesHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }
        
        List<BsonDocument> indexes = getDatabase()
                .getCollectionIndexes(
                        context.getClientSession(),
                        context.getDBName(),
                        context.getCollectionName());

        context.setResponseContent(
                IndexesRepresentationFactory
                .getRepresentation(
                        exchange,
                        context,
                        indexes,
                        indexes.size())
                .asBsonDocument());

        context.setResponseContentType(Resource.HAL_JSON_MEDIA_TYPE);
        context.setResponseStatusCode(HttpStatus.SC_OK);

        next(exchange, context);
    }
}
