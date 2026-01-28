/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
 * Utility class for managing HTTP server exchange attachments related to MongoDB services.
 * This class provides methods to attach and retrieve BSON content from HTTP server exchanges,
 * enabling efficient sharing of parsed MongoDB request data across request processing components.
 *
 * <p>The class uses Undertow's attachment mechanism to store BSON values that can be
 * accessed by different parts of the request processing pipeline without re-parsing.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoServiceAttachments {
    /** Attachment key for storing BSON content in HTTP server exchanges. */
    private static final AttachmentKey<BsonValue> MONGO_REQUEST_CONTENT_KEY = AttachmentKey.create(BsonValue.class);

    /**
     * Retrieves the BSON content attached to the HTTP server exchange.
     * This method returns the parsed BSON value that was previously attached
     * to the exchange during request processing.
     *
     * @param exchange the HTTP server exchange to retrieve BSON content from
     * @return the BsonValue attached to the exchange, or null if no content is attached
     */
    public static BsonValue attachedBsonContent(HttpServerExchange exchange) {
        return exchange.getAttachment(MONGO_REQUEST_CONTENT_KEY);
    }

    /**
     * Attaches BSON content to the HTTP server exchange for later retrieval.
     * This method stores the parsed BSON value in the exchange so it can be
     * accessed by other components in the request processing pipeline.
     *
     * @param exchange the HTTP server exchange to attach BSON content to
     * @param value the BsonValue to attach to the exchange
     */
    public static void attachBsonContent(HttpServerExchange exchange, BsonValue value) {
        exchange.putAttachment(MONGO_REQUEST_CONTENT_KEY, value);
    }
}
