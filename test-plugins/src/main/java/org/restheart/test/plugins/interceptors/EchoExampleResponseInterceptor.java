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
import io.undertow.util.HttpString;
import java.util.Map;
import org.restheart.handlers.exchange.BufferedJsonResponse;
import org.restheart.plugins.InjectConfiguration;
import static org.restheart.plugins.InterceptPoint.RESPONSE;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "echoExampleResponseInterceptor",
        description = "used for testing purposes",
        enabledByDefault = false,
        requiresContent = true,
        interceptPoint = RESPONSE)
public class EchoExampleResponseInterceptor implements Interceptor {
    
    private static final Logger LOGGER = LoggerFactory
            .getLogger(EchoExampleResponseInterceptor.class);
    
    /**
     * shows how to inject configuration via @OnInit
     * @param args
     */
    @InjectConfiguration
    public void init(Map<String, Object> args) {
        LOGGER.trace("got args {}", args);
    }

    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        var response = BufferedJsonResponse.wrap(exchange);

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
        return exchange.getRequestPath().equals("/iecho")
                || exchange.getRequestPath().equals("/piecho")
                || exchange.getRequestPath().equals("/anything");
    }

}
