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
import io.uiam.handlers.exchange.JsonResponse;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.uiam.plugins.interceptors.PluggableResponseInterceptor;
import io.undertow.util.HttpString;

/**
 *
 * An example injector that modifies the json response for a proxied resource,
 * in this case syncBufferedContent() is needed
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ExampleProxiedResponseInterceptor
        implements PluggableResponseInterceptor {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(ExampleProxiedResponseInterceptor.class);

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var response = JsonResponse.wrap(exchange);

        if (response.isContentAvailable()) {
            JsonElement content = response.readContent();

            if (content.isJsonObject()) {
                content.getAsJsonObject().addProperty("wow",
                        "added by ExampleProxiedResponseInterceptor");

                response.writeContent(content);
            }
        }

        exchange.getResponseHeaders().add(HttpString.tryFromString("header"),
                "added by ExampleProxiedResponseInterceptor");
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var req = JsonRequest.wrap(exchange);
        return req.isGet()
                && exchange.getRequestPath().startsWith("/pr/");
    }

    @Override
    public boolean requiresResponseContent() {
        return true;
    }
}
