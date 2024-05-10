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
import java.nio.charset.StandardCharsets;

import org.restheart.utils.ChannelReader;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayRequest extends ServiceRequest<byte[]> {
    protected ByteArrayRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public static ByteArrayRequest init(HttpServerExchange exchange) {
        return new ByteArrayRequest(exchange);
    }

    public static ByteArrayRequest of(HttpServerExchange exchange) {
        return of(exchange, ByteArrayRequest.class);
    }

    @Override
    public byte[] parseContent() throws IOException, BadRequestException {
        if (wrapped.getRequestContentLength() > 0) {
            return ChannelReader.readBytes(wrapped);
        } else {
            return new byte[0];
        }
    }

    public String getContentString() {
        return new String(getContent(), StandardCharsets.UTF_8);
    }
}
