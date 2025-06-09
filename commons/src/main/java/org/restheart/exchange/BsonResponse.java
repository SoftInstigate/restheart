/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import static org.restheart.utils.BsonUtils.ArrayBuilder;
import static org.restheart.utils.BsonUtils.DocumentBuilder;
import static org.restheart.utils.BsonUtils.document;
import static org.restheart.utils.BsonUtils.toJson;

/**
 * ServiceResponse implementation backed by BsonValue
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BsonResponse extends ServiceResponse<BsonValue> {
    protected BsonResponse(HttpServerExchange exchange) {
        super(exchange);
        setContentTypeAsJson();
    }

    public static BsonResponse init(HttpServerExchange exchange) {
        return new BsonResponse(exchange);
    }

    public static BsonResponse of(HttpServerExchange exchange) {
        return BsonResponse.of(exchange, BsonResponse.class);
    }

    @Override
    public String readContent() {
        if (content != null) {
            return toJson(content);
        } else {
            return null;
        }
    }

    public void setContent(ArrayBuilder builder) {
        setContent(builder.get());
    }

    public void setContent(DocumentBuilder builder) {
        setContent(builder.get());
    }

    @Override
    public void setInError(int code, String message, Throwable t) {
        setInError(true);
        setStatusCode(code);

        var content = document();

        if (message != null) {
            content.put("msg", message);
        } else {
            content.putNull("msg");
        }

        if (t != null) {
            content.put("msg", t.getMessage());
        }

        setContent(content);
    }
}
