/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import java.util.Optional;

import org.bson.BsonObjectId;
import org.bson.types.ObjectId;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.interceptors.MetadataCachesSingleton;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteDBHandler extends PipelinedHandler {
    private final Databases dbs = Databases.get();

    /**
     * Creates a new instance of DeleteDBHandler
     */
    public DeleteDBHandler() {
        super();
    }

    /**
     * Creates a new instance of DeleteDBHandler
     *
     * @param next
     */
    public DeleteDBHandler(PipelinedHandler next) {
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

        var etag = request.getETag() == null
            ? null
            : ObjectId.isValid(request.getETag())
            ? new BsonObjectId(new ObjectId(request.getETag()))
            : new BsonObjectId();

        var result = dbs.deleteDatabase(
            Optional.ofNullable(request.getClientSession()),
            request.rsOps(),
            request.getDBName(),
            etag,
            request.isETagCheckRequired());

        response.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            response.setInError(HttpStatus.SC_CONFLICT, "The database's ETag must be provided using the '" + Headers.IF_MATCH + "' header.");
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        MetadataCachesSingleton.getInstance().invalidateDb(request.getDBName());

        next(exchange);
    }
}
