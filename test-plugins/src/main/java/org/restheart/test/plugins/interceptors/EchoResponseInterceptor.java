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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.undertow.util.HttpString;
import java.nio.charset.Charset;
import java.util.Map;
import org.restheart.handlers.exchange.ByteArrayRequest;
import org.restheart.handlers.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayInterceptor;
import org.restheart.plugins.InjectConfiguration;
import static org.restheart.plugins.InterceptPoint.RESPONSE;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BuffersUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "echoResponseInterceptor",
        description = "used for testing purposes",
        enabledByDefault = false,
        requiresContent = true,
        interceptPoint = RESPONSE)
public class EchoResponseInterceptor implements ByteArrayInterceptor {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(EchoResponseInterceptor.class);

    /**
     * shows how to inject configuration via @OnInit
     *
     * @param args
     */
    @InjectConfiguration
    public void init(Map<String, Object> args) {
        LOGGER.trace("got args {}", args);
    }

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        response.getExchange().getResponseHeaders()
                .add(HttpString.tryFromString("header"),
                        "added by echoResponseInterceptor "
                        + request.getPath());

        if (response.getContent() != null && response.isContentTypeJson()) {
            var _content = JsonParser.parseString(
                    new String(response.getContent(), Charset.forName("utf-8")));

            JsonObject content = _content
                    .getAsJsonObject();

            content.addProperty("prop2",
                    "property added by echoResponseInterceptor");
            
            response.setContent(content.toString().getBytes());

        }
    }

    @Override
    public boolean resolve(ByteArrayRequest request, ByteArrayResponse response) {
        return request.getPath().equals("/iecho");
    }
}
