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
import io.undertow.util.HttpString;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class OptionsHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of OptionsHandler
     *
     * OPTIONS is used in CORS preflight phase and needs to be outside the
     * security zone (i.e. not Authorization header required) It is important
     * that OPTIONS responds to any resource URL, regardless its existance: This
     * is because OPTIONS http://restheart.org/employees/tofire/andrea shall not
     * give any information
     *
     * The Access-Control-Allow-Methods header indicates, as part of the
     * response to a preflight request, which methods can be used during the
     * actual request.
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
            next(exchange, context);
            return;
        }

        if (null != context.getType()) {
            switch (context.getType()) {
                case ROOT:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case DB:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case DB_SIZE:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case COLLECTION:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case COLLECTION_SIZE:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case AGGREGATION:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case BULK_DOCUMENTS:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "POST, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case DOCUMENT:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, If-None-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case FILE:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, If-None-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case FILES_BUCKET:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case FILE_BINARY:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case SCHEMA_STORE:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case SCHEMA:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, If-None-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case INDEX:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "PUT")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case COLLECTION_INDEXES:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                default:
                    break;
            }
        }

        exchange.setStatusCode(HttpStatus.SC_OK);
        exchange.endExchange();
    }
}
