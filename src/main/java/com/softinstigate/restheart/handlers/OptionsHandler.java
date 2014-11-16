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
package com.softinstigate.restheart.handlers;

import com.softinstigate.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 *
 * @author Andrea Di Cesare
 */
public class OptionsHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of OptionsHandler
     *
     * OPTIONS is used in CORS preflight phase and needs to be outside the
     * security zone (i.e. not Authorization header required) It is important
     * that OPTIONS responds to any resource URL, regardless its existance: This
     * is because OPTIONS http://restheart/employees/tofire/andrea shall not
     * give any information
     *
     * @param next
     */
    public OptionsHandler(PipedHttpHandler next) {
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
        if (!(context.getMethod() == RequestContext.METHOD.OPTIONS)) {
            next.handleRequest(exchange, context);
            return;
        }

        if (context.getType() == RequestContext.TYPE.ROOT) {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");

        } else if (context.getType() == RequestContext.TYPE.DB) {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
        } else if (context.getType() == RequestContext.TYPE.COLLECTION) {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
        } else if (context.getType() == RequestContext.TYPE.DOCUMENT) {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, If-None-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
        } else if (context.getType() == RequestContext.TYPE.INDEX) {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "PUT")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
        } else if (context.getType() == RequestContext.TYPE.COLLECTION_INDEXES) {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
        }

        exchange.setResponseCode(HttpStatus.SC_OK);
        exchange.endExchange();
    }
}
