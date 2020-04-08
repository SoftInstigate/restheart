/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.handlers.exchange;

import io.undertow.server.HttpServerExchange;
import org.restheart.utils.JsonUtils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ByteArrayResponse extends Response<byte[]> {
    private ByteArrayResponse(HttpServerExchange exchange) {
        super(exchange);
    }
    
    public static ByteArrayResponse init(HttpServerExchange exchange) {
        return new ByteArrayResponse(exchange);
    }
    
    public static ByteArrayResponse wrap(HttpServerExchange exchange) {
        return (ByteArrayResponse) of(exchange);
    }
    
    @Override
    public String readContent() {
        if (content != null) {
            return new String(content);
        } else {
            return null;
        }
    }

    @Override
    public void setInError(int code, String message, Throwable t) {
        setStatusCode(code);
        
        var resp = new StringBuilder();

        if (message != null) {
            resp.append("msg: ");
            resp.append(message);
            resp.append("\n");
        }

        if (t != null) {
            resp.append("exception: ");
            resp.append(t.getMessage());
            resp.append("\n");
        }

        setContent(resp.toString().getBytes());
    }
}
