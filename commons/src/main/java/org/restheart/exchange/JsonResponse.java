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

import org.restheart.utils.GsonUtils.ObjectBuilder;
import org.restheart.utils.GsonUtils.ArrayBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonResponse extends ServiceResponse<JsonElement> {
    protected JsonResponse(HttpServerExchange exchange) {
        super(exchange);
        setContentTypeAsJson();
    }

    public static JsonResponse init(HttpServerExchange exchange) {
        return new JsonResponse(exchange);
    }

    public static JsonResponse of(HttpServerExchange exchange) {
        return JsonResponse.of(exchange, JsonResponse.class);
    }

    @Override
    public String readContent() {
        if (content != null) {
            return content.toString();
        } else {
            return null;
        }
    }

    public void setContent(ObjectBuilder builder) {
        setContent(builder.get());
    }

    public void setContent(ArrayBuilder builder) {
        setContent(builder.get());
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

        setContent(resp);
    }
}
