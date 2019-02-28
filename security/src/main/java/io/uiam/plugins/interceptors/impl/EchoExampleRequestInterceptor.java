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
import com.google.gson.JsonObject;
import io.uiam.handlers.exchange.JsonRequest;
import io.undertow.server.HttpServerExchange;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.uiam.plugins.interceptors.PluggableRequestInterceptor;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class EchoExampleRequestInterceptor implements PluggableRequestInterceptor {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(EchoExampleRequestInterceptor.class);

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // add query parameter ?pagesize=0
        var vals = new LinkedList<String>();
        vals.add("param added by EchoExampleRequestInterceptor");
        exchange.getQueryParameters().put("param", vals);

        var request = JsonRequest.wrap(exchange);

        JsonElement requestContent = null;

        if (!request.isContentAvailable()) {
            request.writeContent(new JsonObject());
        }

        if (request.isContentTypeJson()) {
            requestContent = request.readContent();

            if (requestContent.isJsonObject()) {
                requestContent.getAsJsonObject()
                        .addProperty("prop1",
                                "property added by EchoExampleRequestInterceptor");

                request.writeContent(requestContent);
            }
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return exchange.getRequestPath().equals("/iecho")
                || exchange.getRequestPath().equals("/siecho");
    }

    @Override
    public boolean requiresContent() {
        return true;
    }
}
