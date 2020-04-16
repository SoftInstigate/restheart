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
package org.restheart.mongodb.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.restheart.exchange.BsonRequest;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class OptionsHandler extends PipelinedHandler {
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
     */
    public OptionsHandler() {
        super(null);
    }

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
    public OptionsHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);

        if (!(request.isOptions())) {
            next(exchange);
            return;
        }

        if (null != request.getType()) {
            switch (request.getType()) {
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
                case DB_META:
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
                case COLLECTION_META:
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

                case SESSIONS:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "POST")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;

                case TRANSACTIONS:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;

                case TRANSACTION:
                    exchange.getResponseHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "PATCH, DELETE")
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
