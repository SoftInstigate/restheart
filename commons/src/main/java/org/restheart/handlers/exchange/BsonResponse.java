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
import io.undertow.util.AttachmentKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bson.BsonValue;
import static org.restheart.handlers.exchange.AbstractExchange.LOGGER;
import org.restheart.utils.JsonUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BsonResponse extends Response<BsonValue> {
    private static final AttachmentKey<BsonResponse> BSON_RESPONSE_ATTACHMENT_KEY
            = AttachmentKey.create(BsonResponse.class);

    private BsonValue content;

    private OperationResult dbOperationResult;

    private final List<String> warnings = new ArrayList<>();

    protected BsonResponse(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(BsonResponse.class);
    }

    public static BsonResponse wrap(HttpServerExchange exchange) {
        var cached = exchange.getAttachment(BSON_RESPONSE_ATTACHMENT_KEY);

        if (cached == null) {
            var response = new BsonResponse(exchange);
            exchange.putAttachment(BSON_RESPONSE_ATTACHMENT_KEY,
                    response);
            return response;
        } else {
            return cached;
        }
    }

    public static boolean isInitialized(HttpServerExchange exchange) {
        return (exchange.getAttachment(BSON_RESPONSE_ATTACHMENT_KEY) != null);
    }

    /**
     * @return the content
     */
    public BsonValue getContent() {
        return content;
    }

    /**
     * @param content the content to set
     */
    public void setContent(BsonValue content) {
        this.content = content;

        // This makes the content availabe to ByteArrayResponse
        // core's ResponseSender uses ByteArrayResponse 
        // to send the content to the client
        if (content != null) {
            try {
                ByteArrayResponse.wrap(wrapped)
                        .writeContent(JsonUtils.toJson(content,
                                BsonRequest.wrap(wrapped).getJsonMode())
                                .getBytes());
            } catch (IOException ioe) {
                LOGGER.error("Error writing request content", ioe);
            }
        }
    }

    /**
     * @return the dbOperationResult
     */
    public OperationResult getDbOperationResult() {
        return dbOperationResult;
    }

    /**
     * @param dbOperationResult the dbOperationResult to set
     */
    public void setDbOperationResult(OperationResult dbOperationResult) {
        this.dbOperationResult = dbOperationResult;
    }

    /**
     * @return the warnings
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * @param warning
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }
}
