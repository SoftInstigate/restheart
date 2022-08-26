/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 SoftInstigate
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

import com.google.gson.JsonParser;
import io.undertow.util.HttpString;
import java.nio.charset.Charset;
import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;
import static org.restheart.plugins.InterceptPoint.RESPONSE;
import org.restheart.plugins.ProxyInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BuffersUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "echoProxyResponseInterceptor",
        description = "used for testing purposes",
        enabledByDefault = false,
        requiresContent = true,
        interceptPoint = RESPONSE)
public class EchoProxyResponseInterceptor implements ProxyInterceptor {
    @Override
    public void handle(ByteArrayProxyRequest request, ByteArrayProxyResponse response) throws Exception {
        response.getHeaders().add(HttpString.tryFromString("header"), "added by echoProxyResponseInterceptor " + request.getPath());

        var content = response.readContent();
        if (content != null && response.isContentTypeJson()) {
            var jsonContent = JsonParser.parseString(BuffersUtils.toString(content,Charset.forName("utf-8")));

            var jsonObjectContent = jsonContent.getAsJsonObject();

            jsonObjectContent.addProperty("prop3", "property added by echoProxyResponseInterceptor");

            response.writeContent(jsonObjectContent.toString().getBytes());
        }
    }

    @Override
    public boolean resolve(ByteArrayProxyRequest request, ByteArrayProxyResponse response) {
        return request.getPath().equals("/piecho") && response.isContentAvailable();
    }
}
