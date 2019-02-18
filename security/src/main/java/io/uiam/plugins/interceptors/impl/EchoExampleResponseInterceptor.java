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

import io.uiam.handlers.Response;
import io.undertow.server.HttpServerExchange;
import io.uiam.plugins.interceptors.PluggableResponseInterceptor;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class EchoExampleResponseInterceptor implements PluggableResponseInterceptor{

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        Response.wrap(exchange)
                .getContentAsJson()
                .getAsJsonObject()
                .addProperty("prop2", 
                        "property added by example response interceptor");
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var response = Response.wrap(exchange);
        
        return response.isContentTypeJson() &&
                response.getContentAsJson() != null &&
                response.getContentAsJson().isJsonObject() &&
                (exchange.getRequestPath().equals("/echo") ||
                exchange.getRequestPath().equals("/secho"));
    }
}
