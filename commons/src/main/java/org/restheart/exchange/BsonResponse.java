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
package org.restheart.exchange;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.utils.JsonUtils;

/**
 * Response implementation backed by BsonValue
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BsonResponse extends Response<BsonValue> {
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
            return JsonUtils.toJson(content);
        } else {
            return null;
        }
    }

    @Override
    public void setInError(int code, String message, Throwable t) {
        setStatusCode(code);
        
        var resp = new BsonDocument();

        if (message != null) {
            resp.put("msg", new BsonString(message));
        }

        if (t != null) {
            resp.put("exception", new BsonString(t.getMessage()));
        }

        setContent(resp);
    }
}
