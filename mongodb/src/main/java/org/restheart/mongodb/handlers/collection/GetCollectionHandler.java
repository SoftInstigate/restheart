/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.json.JsonParseException;
import org.restheart.exchange.IllegalQueryParamenterException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetCollectionHandler extends PipelinedHandler {
    private final Databases dbs = Databases.get();
    private final boolean isGetCollectionCacheEnabled = MongoServiceConfiguration.get().isGetCollectionCacheEnabled();

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

        long size = -1;

        if (request.isCount()) {
            size = dbs.getCollectionSize(
                Optional.ofNullable(request.getClientSession()),
                request.rsOps(),
                request.getDBName(),
                request.getCollectionName(),
                request.getFiltersDocument());
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

            data = dbs.getCollectionData(
                Optional.ofNullable(request.getClientSession()),
                request.rsOps(),
                request.getDBName(),
                request.getCollectionName(),
                request.getPage(),
                request.getPagesize(),
                sort,
                filter,
                request.getHintDocument(),
                request.getProjectionDocument(),
                request.isCache() && isGetCollectionCacheEnabled);
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
