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
import io.uiam.handlers.exchange.JsonResponse;
import io.undertow.server.HttpServerExchange;
import io.uiam.plugins.interceptors.PluggableResponseInterceptor;
import io.undertow.util.HttpString;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class EchoExampleResponseInterceptor implements PluggableResponseInterceptor {

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var response = JsonResponse.wrap(exchange);
        
        exchange.getResponseHeaders().add(HttpString.tryFromString("header"),
                "added by EchoExampleResponseInterceptor " + exchange.getRequestPath());

        if (response.isContentAvailable()) {
            JsonElement _content = response
                    .readContent();

            // can be null
            if (_content.isJsonObject()) {
                JsonObject content = _content
                        .getAsJsonObject();

                content.addProperty("prop2",
                        "property added by EchoExampleResponseInterceptor");

                response.writeContent(content);
            }
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return exchange.getRequestPath().equals("/echo") || 
                exchange.getRequestPath().equals("/secho");
    }

    @Override
    public boolean requiresResponseContent() {
        return true;
    }
}
