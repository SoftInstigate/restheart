/*
 * uIAM - the IAM for microservices
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
        return exchange.getRequestPath().equals("/echo")
                || exchange.getRequestPath().equals("/secho");
    }

    @Override
    public boolean requiresContent() {
        return true;
    }
}
