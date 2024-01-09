/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
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
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (!(request.isOptions())) {
            next(exchange);
            return;
        }

        if (null != request.getType()) {
            switch (request.getType()) {
                case ROOT:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case DB:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case DB_SIZE:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case DB_META:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case COLLECTION:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case COLLECTION_SIZE, FILES_BUCKET_SIZE:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case COLLECTION_META:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case AGGREGATION:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case BULK_DOCUMENTS:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "POST, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case DOCUMENT:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, If-None-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case FILE:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, If-None-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case FILES_BUCKET:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case FILE_BINARY:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case SCHEMA_STORE:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case SCHEMA:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, PATCH, DELETE, OPTIONS")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, If-None-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case INDEX:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "PUT")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;
                case COLLECTION_INDEXES:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;

                case SESSIONS:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "POST")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;

                case SESSION:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "DELETE")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;

                case TRANSACTIONS:
                    response.getHeaders()
                            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST")
                            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
                    break;

                case TRANSACTION:
                    response.getHeaders()
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
