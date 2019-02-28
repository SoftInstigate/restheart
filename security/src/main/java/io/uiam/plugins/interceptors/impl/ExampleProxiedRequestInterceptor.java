/*
 * uIAM - the IAM for microservices
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
package io.uiam.plugins.interceptors.impl;

import com.google.gson.JsonElement;
import io.uiam.handlers.exchange.JsonRequest;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.uiam.plugins.interceptors.PluggableRequestInterceptor;

/**
 *
 * An example injector that modifies a json request for a proxied resource, in
 * this case syncBufferedContent() is needed
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ExampleProxiedRequestInterceptor
        implements PluggableRequestInterceptor {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(ExampleProxiedRequestInterceptor.class);

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = JsonRequest.wrap(exchange);

        if (request.isContentAvailable()) {
            JsonElement content = request.readContent();

            if (content.isJsonObject()) {
                content.getAsJsonObject()
                        .addProperty("modified", true);
                
                request.writeContent(content);
            }
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var req = JsonRequest.wrap(exchange);
        return !req.isGet() && req.isContentTypeJson()
                && exchange.getRequestPath().startsWith("/pr");
    }

    @Override
    public boolean requiresContent() {
        return true;
    }
}
