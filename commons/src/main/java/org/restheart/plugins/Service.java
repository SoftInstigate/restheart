/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins;

import java.util.function.Consumer;
import java.util.function.Function;

import org.restheart.exchange.Response;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Services allow to extend the API adding web services
 *
 * @param <R> Request the request type
 * @param <S> Response the response type
 * Seehttps://restheart.org/docs/plugins/core-plugins/#services
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Service<R extends ServiceRequest<?>, S extends ServiceResponse<?>>
                extends HandlingPlugin<R, S>, ConfigurablePlugin {
        /**
         * handle the request
         *
         * @param request
         * @param response
         * @throws Exception
         */
        public void handle(final R request, final S response) throws Exception;

        /**
         *
         * @return the function used to instantiate the request object
         */
        public Consumer<HttpServerExchange> requestInitializer();

        /**
         *
         * @return the function used to instantiate the response object
         */
        public Consumer<HttpServerExchange> responseInitializer();

        /**
         *
         * @return the function used to retrieve the request object
         */
        public Function<HttpServerExchange, R> request();

        /**
         *
         * @return the function used to retrieve the response object
         */
        public Function<HttpServerExchange, S> response();

        /**
         * helper method to handle OPTIONS requests
         *
         * @param request
         * @throws Exception
         */
        default void handleOptions(final R request) {
            var exchange = request.getExchange();
            var response = Response.of(exchange);

            response.getHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Methods"),
                    "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                .put(HttpString.tryFromString("Access-Control-Allow-Headers"),
                    "Accept, Accept-Encoding, Authorization, "
                    + "Content-Length, Content-Type, Host, "
                    + "If-Match, Origin, X-Requested-With, "
                    + "User-Agent, No-Auth-Challenge");

            response.setStatusCode(HttpStatus.SC_OK);
            exchange.endExchange();
        }
}
