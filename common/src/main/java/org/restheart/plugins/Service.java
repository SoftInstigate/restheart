/*
 * RESTHeart Common
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.plugins;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.restheart.utils.HttpStatus;

/**
 * @see https://restheart.org/docs/develop/security-plugins/#services
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Service extends HandlingPlugin {
    @Override
    default boolean resolve(HttpServerExchange exchange) {
        return true;
    }
    
     /**
     * helper method to handle OPTIONS requests
     *
     * @param exchange
     * @throws Exception
     */
    default void handleOptions(HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
        exchange.setStatusCode(HttpStatus.SC_OK);
        exchange.endExchange();
    }
}
