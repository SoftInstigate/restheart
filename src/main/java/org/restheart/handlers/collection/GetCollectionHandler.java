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
package org.restheart.handlers.collection;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.util.JSONParseException;
import org.restheart.utils.HttpStatus;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import org.restheart.db.Database;
import org.restheart.db.DbsDAO;
import org.restheart.hal.Representation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GetCollectionHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCollectionHandler.class);

    public GetCollectionHandler() {
        super();
    }

    public GetCollectionHandler(PipedHttpHandler next) {
        super(next, new DbsDAO());
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
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        DBCollection coll = getDatabase().getCollection(context.getDBName(), context.getCollectionName());
        long size = -1;

        if (context.isCount()) {
            size = getDatabase().getCollectionSize(coll, exchange.getQueryParameters().get("filter"));
        }

        // ***** get data
        ArrayList<DBObject> data = null;

        if (context.getPagesize() > 0) {

            try {
                data = getDatabase().getCollectionData(coll, context.getPage(), context.getPagesize(),
                        context.getSortBy(), context.getFilter(), context.getCursorAllocationPolicy());
            } catch (JSONParseException jpe) {
                // the filter expression is not a valid json string
                LOGGER.debug("invalid filter expression {}", context.getFilter(), jpe);
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "wrong request, filter expression is invalid", jpe);
                return;
            } catch (MongoException me) {
                if (me.getMessage().matches(".*Can't canonicalize query.*")) {
                    // error with the filter expression during query execution
                    LOGGER.debug("invalid filter expression {}", context.getFilter(), me);
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "wrong request, filter expression is invalid", me);
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

        // ***** return NOT_FOUND from here if collection is not existing 
        // (this is to avoid to check existance via the slow CollectionDAO.checkCollectionExists)
        if ((context.getPagesize() > 0 && (data == null || data.isEmpty())) && (context.getCollectionProps() == null || context.getCollectionProps().keySet().isEmpty())) {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return;
        }

        try {
            CollectionRepresentationFactory crp = new CollectionRepresentationFactory();
            Representation rep = crp.getRepresentation(exchange, context, data, size);

            exchange.setResponseCode(HttpStatus.SC_OK);

            // call the ResponseScriptMetadataHanlder if piped in
            if (getNext() != null) {
                DBObject responseContent = rep.asDBObject();
                context.setResponseContent(responseContent);

                getNext().handleRequest(exchange, context);
            }

            crp.sendRepresentation(exchange, context, rep);
            exchange.endExchange();
        } catch (IllegalQueryParamenterException ex) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
