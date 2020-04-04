/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.test.plugins.interceptors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import java.util.LinkedList;
import org.restheart.handlers.exchange.JsonRequest;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.RegisterPlugin;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "echoExampleRequestInterceptor",
        description = "used for testing purposes",
        enabledByDefault = false,
        requiresContent = true)
public class EchoExampleRequestInterceptor implements Interceptor {
    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        // add query parameter ?pagesize=0
        var vals = new LinkedList<String>();
        vals.add("param added by EchoExampleRequestInterceptor");
        exchange.getQueryParameters().put("param", vals);

        var request = JsonRequest.wrap(exchange);

        JsonElement requestContent;

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
                || exchange.getRequestPath().equals("/anything")
                || exchange.getRequestPath().equals("/restheart/coll");
    }
}
