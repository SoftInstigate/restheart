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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.charset.Charset;
import java.util.LinkedList;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BuffersUtils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "echoRequestInterceptor",
        description = "used for testing purposes",
        enabledByDefault = false,
        requiresContent = true)
public class EchoRequestInterceptor implements ByteArrayInterceptor {
    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        // add query parameter ?pagesize=0
        var vals = new LinkedList<String>();
        vals.add("param added by echoRequestInterceptor");
        request.getExchange().getQueryParameters().put("param", vals);

        if (request.isContentTypeJson()) {
            try {
                JsonElement requestContent = JsonParser.parseString(
                        BuffersUtils.toString(request.getContent(),
                                Charset.forName("utf-8")));

                if (requestContent.isJsonObject()) {
                    requestContent.getAsJsonObject()
                            .addProperty("prop1",
                                    "property added by echoRequestInterceptor");
                }

                request.setContent(requestContent.toString().getBytes());
            } catch (Throwable t) {

            }
        }

    }

    @Override
    public boolean resolve(ByteArrayRequest request, ByteArrayResponse response) {
        return request.getPath().equals("/iecho");
    }
}
