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
import java.io.IOException;
import org.bson.BsonValue;
import org.restheart.utils.ChannelReader;
import org.restheart.utils.JsonUtils;

/**
 * ServiceRequest implementation backed by BsonValue
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BsonRequest extends ServiceRequest<BsonValue> {
    protected BsonRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public static BsonRequest init(HttpServerExchange exchange) {
        var ret = new BsonRequest(exchange);

        try {
            ret.injectContent();
        } catch (Throwable ieo) {
            ret.setInError(true);
        }

        return ret;
    }

    public static BsonRequest of(HttpServerExchange exchange) {
        return of(exchange, BsonRequest.class);
    }

    public void injectContent() throws IOException {
        var bson = JsonUtils.parse(ChannelReader
                .read(wrapped.getRequestChannel()));

        setContent(bson);
    }
}
