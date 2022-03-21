/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.mongodb.handlers.collection;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.MongoException;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.json.JsonParseException;
import org.restheart.exchange.IllegalQueryParamenterException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetCollectionHandler extends PipelinedHandler {
    private Databases dbs = Databases.get();

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCollectionHandler.class);

    /**
     *
     */
    public GetCollectionHandler() {
        super();
    }

    /**
     *
     * @param next
     */
    public GetCollectionHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param next
     * @param dbsDAO
     */
    @VisibleForTesting
    public GetCollectionHandler(PipelinedHandler next, Databases dbsDAO) {
        super(next);
        this.dbs = dbsDAO;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        var coll = dbs.getCollection(request.getDBName(), request.getCollectionName());

        long size = -1;

        if (request.isCount()) {
            size = dbs.getCollectionSize(Optional.ofNullable(request.getClientSession()), coll, request.getFiltersDocument());
        }

        // ***** get data
        BsonArray data = null;

        if (request.getPagesize() > 0) {
            BsonDocument filter, sort;

            try {
                filter = request.getFiltersDocument();
            } catch (JsonParseException jpe) {
                // invalid filter parameter
                LOGGER.debug("invalid filter parameter {}", request.getFilter(), jpe);
                MongoResponse.of(exchange).setInError(HttpStatus.SC_BAD_REQUEST, "Invalid filter parameter");
                next(exchange);
                return;
            }

            try {
                sort = request.getSortByDocument();
            } catch (JsonParseException jpe) {
                // invalid sort parameter
                LOGGER.debug("invalid sort parameter {}", request.getFilter(), jpe);
                MongoResponse.of(exchange).setInError(HttpStatus.SC_BAD_REQUEST, "Invalid sort parameter");
                next(exchange);
                return;
            }

            try {
                data = dbs.getCollectionData(
                        Optional.ofNullable(request.getClientSession()),
                        coll,
                        request.getPage(),
                        request.getPagesize(),
                        sort,
                        filter,
                        request.getHintDocument(),
                        request.getProjectionDocument(),
                        request.getCursorAllocationPolicy());
            } catch (MongoException me) {
                if (me.getMessage().matches(".*Can't canonicalize query.*")) {
                    // error with the filter expression during query execution
                    LOGGER.debug("invalid filter expression {}", request.getFilter(), me);
                    MongoResponse.of(exchange).setInError(HttpStatus.SC_BAD_REQUEST, "wrong request, filter expression is invalid", me);
                    next(exchange);
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
            response.setContent(data);
            response.setCount(size);

            response.setContentTypeAsJson();
            response.setStatusCode(HttpStatus.SC_OK);

            ResponseHelper.injectEtagHeader(exchange, request.getCollectionProps());

            // call the ResponseTransformerMetadataHandler if piped in
            next(exchange);
        } catch (IllegalQueryParamenterException ex) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
            next(exchange);
        }
    }
}
