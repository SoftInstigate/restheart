/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

package org.restheart.utils;

import org.bson.BsonValue;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Util class for exchange attachments related to the MongoService
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoServiceAttachments {
    private static final AttachmentKey<BsonValue> MONGO_REQUEST_CONTENT_KEY = AttachmentKey.create(BsonValue.class);

    /**
     *
     * @param exchange
     * @return the BsonValue attached to the exchange
     */
    public static BsonValue attachedBsonContent(HttpServerExchange exchange) {
        return exchange.getAttachment(MONGO_REQUEST_CONTENT_KEY);
    }

    /**
     * set the intialized flag for MongoRequest
     *
     * @param exchange
     * @param value the BsonValue to attache to the exchange
     */
    public static void attachBsonContent(HttpServerExchange exchange, BsonValue value) {
        exchange.putAttachment(MONGO_REQUEST_CONTENT_KEY, value);
    }
}
