/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.exchange;

import java.io.IOException;

import org.restheart.utils.ChannelReader;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonRequest extends ServiceRequest<JsonElement> {
    protected JsonRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public static JsonRequest init(HttpServerExchange exchange) {
        return new JsonRequest(exchange);
    }

    public static JsonRequest of(HttpServerExchange exchange) {
        return of(exchange, JsonRequest.class);
    }

    @Override
    public JsonElement parseContent() throws IOException, BadRequestException {
        if (wrapped.getRequestContentLength() > 0) {
            try {
                return JsonParser.parseString(ChannelReader.readString(wrapped));
            } catch(JsonSyntaxException jse) {
                throw new BadRequestException(jse.getMessage(), jse);
            }
        } else {
            return null;
        }
    }
}
