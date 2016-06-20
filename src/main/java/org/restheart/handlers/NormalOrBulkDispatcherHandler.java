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
import org.bson.BsonValue;

/**
 * this handler dispatches request to normal or bulk post collection handlers
 * depending on the content to be an object or an array
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class NormalOrBulkDispatcherHandler extends PipedHttpHandler {
    private final PipedHttpHandler nextNormal;
    private final PipedHttpHandler nextBulk;

    /**
     * Creates a new instance of PostCollectionHandler
     * @param nextNormal next handler for normal requests
     * @param nextBulk next handler for bulk requests
     */
    public NormalOrBulkDispatcherHandler(
            PipedHttpHandler nextNormal, 
            PipedHttpHandler nextBulk) {
        super(null);
        
        this.nextNormal = nextNormal;
        this.nextBulk = nextBulk;
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
            RequestContext context) throws Exception {
        BsonValue content = context.getContent();

        if (content != null 
                && content.isArray()) {
            nextBulk.handleRequest(exchange, context);
        } else {
            nextNormal.handleRequest(exchange, context);
        }
    }
}