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
package org.restheart.mongodb.handlers.collection;

import java.util.Optional;

import org.bson.BsonValue;
import static org.restheart.exchange.ExchangeKeys._SCHEMAS;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.interceptors.MetadataCachesSingleton;
import org.restheart.mongodb.utils.RequestHelper;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PatchCollectionHandler extends PipelinedHandler {
    private final Databases dbs = Databases.get();

    /**
     * Creates a new instance of PatchCollectionHandler
     */
    public PatchCollectionHandler() {
        super();
    }

    /**
     * Creates a new instance of PatchCollectionHandler
     *
     * @param next
     */
    public PatchCollectionHandler(PipelinedHandler next) {
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

        if (request.getDBName().isEmpty()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "wrong request, db name cannot be empty");
            next(exchange);
            return;
        }

        if (request.getCollectionName().isEmpty() || (request.getCollectionName().startsWith("_") && !request.getCollectionName().equals(_SCHEMAS))) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "wrong request, collection name cannot be empty or start with _");
            next(exchange);
            return;
        }

        BsonValue _content = request.getContent();

        if (_content == null) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "no data provided");
            next(exchange);
            return;
        }

        // cannot PATCH with an array
        if (!_content.isDocument()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "data must be a json object");
            next(exchange);
            return;
        }

        if (_content.asDocument().isEmpty()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "no data provided");
            next(exchange);
            return;
        }

        final var content = _content.asDocument();

        var result = dbs.upsertCollection(
            Optional.ofNullable(request.getClientSession()),
            request.rsOps(),
            request.getDBName(),
            request.getCollectionName(),
            request.getMethod(),
            request.getCollectionProps() != null, // true if updating
            content,
            request.getETag(),
            request.isETagCheckRequired());

        if (RequestHelper.isResponseInConflict(result, exchange)) {
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        MetadataCachesSingleton.getInstance().invalidateCollection(request.getDBName(), request.getCollectionName());

        next(exchange);
    }

}
