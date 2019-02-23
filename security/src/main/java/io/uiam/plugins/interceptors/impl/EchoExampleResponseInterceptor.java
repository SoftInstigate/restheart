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

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.uiam.handlers.Response;
import io.undertow.server.HttpServerExchange;
import io.uiam.plugins.interceptors.PluggableResponseInterceptor;
import io.uiam.utils.HttpStatus;
import java.io.IOException;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class EchoExampleResponseInterceptor implements PluggableResponseInterceptor {

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        try {
            var response = Response.wrap(exchange);

            response
                    .getContentAsJson()
                    .getAsJsonObject()
                    .addProperty("prop2",
                            "property added by example response interceptor");

            
            // TODO is that really required, can we automate synch?
            response.syncBufferedContent(exchange);
        }
        catch (IOException | JsonSyntaxException ex) {
            Response.wrap(exchange).endExchangeWithMessage(
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "error",
                    ex);
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return Response.wrap(exchange).isContentTypeJson()
                && (exchange.getRequestPath().equals("/echo")
                || exchange.getRequestPath().equals("/secho"));
    }

    @Override
    public boolean requiresResponseContent() {
        return true;
    }
}
