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

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import org.bson.BsonDocument;
import org.bson.json.JsonParseException;
import org.restheart.db.Database;
import org.restheart.db.DatabaseImpl;
import org.restheart.representation.Resource;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetCollectionHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(GetCollectionHandler.class);

    public GetCollectionHandler() {
        super();
    }

    public GetCollectionHandler(PipedHttpHandler next) {
        super(next, new DatabaseImpl());
    }

    public GetCollectionHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
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

        MongoCollection<BsonDocument> coll = getDatabase()
                .getCollection(
                        context.getDBName(),
                        context.getCollectionName());

        long size = -1;

        if (context.isCount()) {
            size = getDatabase()
                    .getCollectionSize(context.getClientSession(), 
                            coll, context.getFiltersDocument());
        }

        // ***** get data
        ArrayList<BsonDocument> data = null;

        if (context.getPagesize() > 0) {

            try {
                data = getDatabase().getCollectionData(
                        context.getClientSession(), 
                        coll,
                        context.getPage(),
                        context.getPagesize(),
                        context.getSortByDocument(),
                        context.getFiltersDocument(),
                        context.getHintDocument(),
                        context.getProjectionDocument(),
                        context.getCursorAllocationPolicy());
            } catch (JsonParseException jpe) {
                // the filter expression is not a valid json string
                LOGGER.debug("invalid filter expression {}",
                        context.getFilter(), jpe);
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "wrong request, filter expression is invalid",
                        jpe);
                next(exchange, context);
                return;
            } catch (MongoException me) {
                if (me.getMessage().matches(".*Can't canonicalize query.*")) {
                    // error with the filter expression during query execution
                    LOGGER.debug(
                            "invalid filter expression {}",
                            context.getFilter(),
                            me);

                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_BAD_REQUEST,
                            "wrong request, filter expression is invalid",
                            me);
                    next(exchange, context);
                    return;
                } else {
                    throw me;
                }
            }
        }

        if (exchange.isComplete()) {
            // if an error occured getting data, the exchange is already closed
            return;
        }

        try {
            context.setResponseContent(new CollectionRepresentationFactory()
                    .getRepresentation(exchange, context, data, size)
                    .asBsonDocument());

            context.setResponseContentType(Resource.HAL_JSON_MEDIA_TYPE);
            context.setResponseStatusCode(HttpStatus.SC_OK);

            ResponseHelper
                    .injectEtagHeader(exchange, context.getCollectionProps());

            // call the ResponseTransformerMetadataHandler if piped in
            next(exchange, context);
        } catch (IllegalQueryParamenterException ex) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    ex.getMessage(),
                    ex);
            next(exchange, context);
        }
    }
}
