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

import java.io.IOException;

import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.ChannelReader;

import io.undertow.server.HttpServerExchange;

/**
 * ServiceRequest implementation backed by BsonValue
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BsonRequest extends ServiceRequest<BsonValue> implements RawBodyAccessor<String> {

    private String rawBody;

    protected BsonRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public static BsonRequest init(HttpServerExchange exchange) {
        return new BsonRequest(exchange);
    }

    public static BsonRequest of(HttpServerExchange exchange) {
        return of(exchange, BsonRequest.class);
    }

    @Override
    public BsonValue parseContent() throws IOException, BadRequestException {
        try {
            rawBody = ChannelReader.readString(wrapped);
            setRawBody(rawBody);
            return BsonUtils.parse(rawBody);
        } catch (JsonParseException jpe) {
            throw new BadRequestException(jpe.getMessage(), jpe);
        }
    }

    @Override
    public final String getRawBody() {
        return rawBody;
    }

    void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }
}
