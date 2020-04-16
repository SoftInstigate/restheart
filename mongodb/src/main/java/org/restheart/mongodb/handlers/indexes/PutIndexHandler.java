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
package org.restheart.mongodb.handlers.indexes;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PutIndexHandler extends PipelinedHandler {
    private final DatabaseImpl dbsDAO = new DatabaseImpl();

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
    public void handleRequest(HttpServerExchange exchange) 
            throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        
        if (request.isInError()) {
            next(exchange);
            return;
        }
        
        final String db = request.getDBName();
        final String co = request.getCollectionName();
        final String id = request.getIndexId();

        if (id.startsWith("_")) {
            response.setIError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "index name cannot start with _");
            next(exchange);
            return;
        }

        final BsonValue _content = request.getContent();
        
        // must be an object
        if (!_content.isDocument()) {
            response.setIError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data cannot be an array");
            next(exchange);
            return;
        }
        
        BsonDocument content = _content.asDocument();

        BsonValue _keys = content.get("keys");
        BsonValue _ops = content.get("ops");
        
        // must be an object, mandatory
        if (_keys == null || !_keys.isDocument()) {
            response.setIError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "keys must be a json object");
            next(exchange);
            return;
        }
        
        // must be an object, optional
        if (_ops != null && !_ops.isDocument()) {
            response.setIError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "ops must be a json object");
            next(exchange);
            return;
        }
        
        BsonDocument keys = _keys.asDocument();
        BsonDocument ops = _ops == null ? null : _ops.asDocument();

        if (keys == null) {
            response.setInError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "wrong request, content must include 'keys' object", null);
            next(exchange);
            return;
        }

        if (ops == null) {
            ops = new BsonDocument();
        }

        ops.put("name", new BsonString(id));

        try {
            dbsDAO.createIndex(
                    request.getClientSession(),
                    db, co, keys, ops);
        } catch (Throwable t) {
            response.setInError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "error creating the index", 
                    t);
            next(exchange);
            return;
        }

        response.setStatusCode(HttpStatus.SC_CREATED);
        
        next(exchange);
    }
}
