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

import io.uiam.handlers.Request;
import io.uiam.handlers.Response;
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
    public void handleRequest(HttpServerExchange exchange) {
        var res = Response.wrap(exchange);

        try {
        if (res.getContentAsJson() != null) {
            res.getContentAsJson().getAsJsonObject().addProperty("wow", 
                    "added by ExampleProxiedResponseInterceptor");
        }
        } catch(Throwable ioe) {
            LOGGER.error("error getting json", ioe);
        }
        
        exchange.getResponseHeaders().add(HttpString.tryFromString("header"),
                "added by ExampleProxiedResponseInterceptor");
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var req = Request.wrap(exchange);
        return req.isGet()
                && exchange.getRequestPath().startsWith("/pr/");
    }

    @Override
    public boolean requiresResponseContent() {
        return true;
    }
}
