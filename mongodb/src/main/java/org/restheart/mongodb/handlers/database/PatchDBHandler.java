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
package org.restheart.mongodb.handlers.database;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.OperationResult;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.plugins.interceptors.LocalCachesSingleton;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PatchDBHandler extends PipelinedHandler {
    private final DatabaseImpl dbsDAO = new DatabaseImpl();

    /**
     * Creates a new instance of PatchDBHandler
     */
    public PatchDBHandler() {
        super();
    }

    /**
     * Creates a new instance of PatchDBHandler
     *
     * @param next
     */
    public PatchDBHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     * partial update db properties
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange)
            throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
    
        if (request.isInError()) {
            next(exchange);
            return;
        }

        if (request.getDBName().isEmpty()
                || request.getDBName().startsWith("_")) {
            response.setIError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "wrong request, db name cannot be empty or start with _");
            next(exchange);
            return;
        }

        BsonValue _content = request.getContent();

        if (_content == null) {
            response.setIError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            next(exchange);
            return;
        }

        // cannot PATCH an array
        if (!_content.isDocument()) {
            response.setIError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data must be a json object");
            next(exchange);
            return;
        }

        BsonDocument content = _content.asDocument();

        OperationResult result = dbsDAO.upsertDB(
                request.getClientSession(),
                request.getDBName(),
                content,
                request.getETag(),
                true,
                true,
                request.isETagCheckRequired());

        response.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            response.setIError(
                    HttpStatus.SC_CONFLICT,
                    "The database's ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header.");
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        LocalCachesSingleton.getInstance().invalidateDb(request.getDBName());

        next(exchange);
    }
}
