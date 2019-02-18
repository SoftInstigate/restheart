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
import com.google.gson.JsonSyntaxException;
import io.uiam.handlers.Request;
import io.undertow.server.HttpServerExchange;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.uiam.plugins.interceptors.PluggableRequestInterceptor;
import java.io.IOException;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class EchoExampleRequestInterceptor implements PluggableRequestInterceptor {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(EchoExampleRequestInterceptor.class);

    @Override
    public void handle(HttpServerExchange exchange) {
        var request = Request.wrap(exchange);

        // add query parameter ?pagesize=0
        var vals = new LinkedList<String>();
        vals.add("0");
        exchange.getQueryParameters().put("pagesize", vals);

        if (request.isContentTypeJson()) {
            JsonElement requestContent = null;

            try {
                requestContent = request.getContentAsJson();
            } catch (IOException | JsonSyntaxException ex) {
                LOGGER.error("error parsing request content as Json");
            }
            if (requestContent != null) {
                requestContent.getAsJsonObject()
                        .addProperty("prop1",
                                "property added by example request interceptor");
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
