/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package com.softinstigate.restheart.security.handlers;

import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;

/**
 *
 * @author Andrea Di Cesare
 */
public class CORSHandler extends PipedHttpHandler {

    private final HttpHandler noPipedNext;

    /**
     * Creates a new instance of GetRootHandler
     *
     * @param next
     */
    public CORSHandler(PipedHttpHandler next) {
        super(next);
        this.noPipedNext = null;
    }

    /**
     * Creates a new instance of GetRootHandler
     *
     * @param next
     */
    public CORSHandler(HttpHandler next) {
        super(null);
        this.noPipedNext = next;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        injectAccessControlAllowHeaders(exchange);

        if (noPipedNext != null) {
            noPipedNext.handleRequest(exchange);
        } else {
            next.handleRequest(exchange, context);
        }
    }

    private static void injectAccessControlAllowHeaders(HttpServerExchange exchange) {
        HeaderValues vals = exchange.getRequestHeaders().get(HttpString.tryFromString("Origin"));
        if (vals != null && !vals.isEmpty()) {
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Origin"), vals.getFirst());
        } else {
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Origin"), "*");
        }

        exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Credentials"), "true");

    }
}
