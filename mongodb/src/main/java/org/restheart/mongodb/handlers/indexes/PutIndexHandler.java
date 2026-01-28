/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.handlers.indexes;

import java.util.Optional;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Databases;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PutIndexHandler extends PipelinedHandler {
    private final Databases dbs = Databases.get();

    /**
     * Creates a new instance of PutIndexHandler
     */
    public PutIndexHandler() {
        super();
    }

    /**
     * Creates a new instance of PutIndexHandler
     * @param next
     */
    public PutIndexHandler(PipelinedHandler next) {
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

        final String id = request.getIndexId();

        if (id.startsWith("_")) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "index name cannot start with _");
            next(exchange);
            return;
        }

        final BsonValue _content = request.getContent();

        // must be an object
        if (!_content.isDocument()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "data cannot be an array");
            next(exchange);
            return;
        }

        var content = _content.asDocument();

        var _keys = content.get("keys");
        var _ops = content.get("ops");

        // must be an object, mandatory
        if (_keys == null || !_keys.isDocument()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "keys must be a json object");
            next(exchange);
            return;
        }

        // must be an object, optional
        if (_ops != null && !_ops.isDocument()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "ops must be a json object");
            next(exchange);
            return;
        }

        var ops = _ops == null ? new BsonDocument() : _ops.asDocument();
        ops.put("name", new BsonString(id));

        var keys = _keys.asDocument();

        if (keys == null) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "wrong request, content must include 'keys' object", null);
            next(exchange);
            return;
        }

        try {
            dbs.createIndex(
                Optional.ofNullable(request.getClientSession()),
                request.rsOps(),
                request.getDBName(),
                request.getCollectionName(),
                keys,
                Optional.of(ops));
        } catch (Throwable t) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "error creating the index", t);
            next(exchange);
            return;
        }

        response.setStatusCode(HttpStatus.SC_CREATED);

        next(exchange);
    }
}
