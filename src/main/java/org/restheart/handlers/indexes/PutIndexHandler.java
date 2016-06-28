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
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.document.DocumentRepresentationFactory;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PutIndexHandler extends PipedHttpHandler {

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
    public PutIndexHandler(PipedHttpHandler next) {
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
        final String db = context.getDBName();
        final String co = context.getCollectionName();
        final String id = context.getIndexId();

        if (id.startsWith("_")) {
            ResponseHelper.endExchangeWithMessage(
                    exchange, 
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "index name cannot start with _");
            return;
        }

        final BsonValue _content = context.getContent();
        
        // must be an object
        if (!_content.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data cannot be an array");
            return;
        }
        
        BsonDocument content = _content.asDocument();

        BsonValue _keys = content.get("keys");
        BsonValue _ops = content.get("ops");
        
        // must be an object
        if (!_keys.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "keys must be a json object");
            return;
        }
        
        // must be an object
        if (!_ops.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "ops must be a json object");
            return;
        }
        
        BsonDocument keys = _keys.asDocument();
        BsonDocument ops = _ops.asDocument();

        if (keys == null) {
            ResponseHelper.endExchangeWithMessage(
                    exchange, 
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "wrong request, content must include 'keys' object", null);
            return;
        }

        if (ops == null) {
            ops = new BsonDocument();
        }

        ops.put("name", new BsonString(id));

        try {
            getDatabase().createIndex(db, co, keys, ops);
        } catch (Throwable t) {
            ResponseHelper.endExchangeWithMessage(
                    exchange, 
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "error creating the index", 
                    t);
            return;
        }

        exchange.setStatusCode(HttpStatus.SC_CREATED);

        // send the warnings if any
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            DocumentRepresentationFactory rf = new DocumentRepresentationFactory();
            
            rf.sendRepresentation(
                    exchange, 
                    context, 
                    rf.getRepresentation(exchange.getRequestPath(),
                    exchange, 
                    context, 
                    new BsonDocument()));
        }
        
        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }

        exchange.endExchange();
    }
}
