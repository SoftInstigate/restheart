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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonValue;
import org.restheart.utils.JsonUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseSenderHandler extends PipedHttpHandler {

    /**
     * @param next
     */
    public ResponseSenderHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        BsonValue responseContent = context.getResponseContent();
        
        /*if (context.getWarnings() != null) {
            context.getWarnings().forEach(w -> rep.addWarning(w));
        }*/

        exchange.getResponseHeaders().put(
                Headers.CONTENT_TYPE, context.getResponseContentType());
        
        exchange.setStatusCode(context.getResponseStatusCode());
        
        if (responseContent != null) {
            exchange.getResponseSender().send(
                    JsonUtils.toJson(responseContent));
        }
        
        exchange.endExchange();
        
        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }
}
