/*
 * RESTHeart Security
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
package org.restheart.security.plugins;

import io.undertow.server.HttpServerExchange;

/**
 * Base interface for RequestInterceptor and ResponseInterceptor
 * 
 * @see https://restheart.org/docs/develop/security-plugins/#interceptors
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface Interceptor extends ConfigurablePlugin {
    /**
     * implements the interceptor logic
     *
     * @param exchange
     * @throws Exception
     */
    public void handleRequest(final HttpServerExchange exchange) throws Exception;

    /**
     *
     * @param exchange
     * @return true if the interceptor must handle the request
     */
    public boolean resolve(final HttpServerExchange exchange);
}
