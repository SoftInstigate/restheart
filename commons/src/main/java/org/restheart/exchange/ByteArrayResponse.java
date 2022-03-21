/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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

import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayResponse extends ServiceResponse<byte[]> {
    protected ByteArrayResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    public static ByteArrayResponse init(HttpServerExchange exchange) {
        return new ByteArrayResponse(exchange);
    }

    public static ByteArrayResponse of(HttpServerExchange exchange) {
        return of(exchange, ByteArrayResponse.class);
    }

    @Override
    public String readContent() {
        if (content != null) {
            return new String(content, StandardCharsets.UTF_8);
        } else {
            return null;
        }
    }

    @Override
    public void setInError(int code, String message, Throwable t) {
        setInError(true);
        setStatusCode(code);

        var resp = new JsonObject();

        if (message != null) {
            resp.addProperty("msg", message);
        }

        if (t != null) {
            resp.addProperty("exception", t.getMessage());
        }

        setContentTypeAsJson();
        setContent(resp.toString().getBytes());
    }

    public void setContent(String content) {
        if (content != null) {
            setContent(content.getBytes());
        } else {
            setContent((byte[])null);
        }
    }
}
