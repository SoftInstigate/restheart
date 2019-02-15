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
import io.uiam.handlers.ExchangeHelper;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.uiam.plugins.interceptors.PluggableRequestInterceptor;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class TestRequestInterceptor implements PluggableRequestInterceptor {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(TestRequestInterceptor.class);

    @Override
    public void handle(HttpServerExchange exchange) {
        var eh = new ExchangeHelper(exchange);

        // add query parameter ?pagesize=0
        var vals = new LinkedList<String>();
        vals.add("0");

        exchange.getQueryParameters().put("pagesize", vals);

        try {
            // modify the request body adding a json property

            eh.getRequestBodyAsJson().getAsJsonObject()
                    .addProperty("prop1", "property added by test request transformer");
        }
        catch (IOException | JsonSyntaxException ex) {
            LOGGER.error("error parsing request content as Json");
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        ExchangeHelper eh = new ExchangeHelper(exchange);
        
        if (!eh.isRequesteContentTypeJson()) {
            return false;
        }

        JsonElement requestContent = null;

        try {
            requestContent = eh.getRequestBodyAsJson();
        }
        catch (IOException | JsonSyntaxException ex) {
            throw new RuntimeException(ex);
        }

        return requestContent != null
                && requestContent.isJsonObject()
                && (exchange.getRequestPath().equals("/echo")
                || exchange.getRequestPath().equals("/secho"));
    }
}
