/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
 * ServiceResponse implementation that handles BSON (Binary JSON) content for responses.
 * <p>
 * This class provides a specialized response handler for sending BSON content back to clients
 * as JSON. It extends ServiceResponse and automatically sets the content type to JSON.
 * The class handles the conversion from BSON objects to JSON strings for HTTP transmission.
 * </p>
 * <p>
 * BsonResponse is commonly used by MongoDB services that need to return query results,
 * documents, or other BSON-compatible data structures to clients. It provides convenient
 * methods for setting content using BSON builders and handling error responses.
 * </p>
 * <p>
 * The response content is automatically serialized to JSON format when sent to the client,
 * ensuring compatibility with standard HTTP clients and web browsers.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BsonResponse extends ServiceResponse<BsonValue> {
    /**
     * Constructs a new BsonResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. It automatically sets the response content type to JSON.
     * Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected BsonResponse(HttpServerExchange exchange) {
        super(exchange);
        setContentTypeAsJson();
    }

    /**
     * Factory method to create a new BsonResponse instance.
     * <p>
     * This method creates a fresh BsonResponse instance for the given exchange
     * with the content type automatically set to JSON.
     * </p>
     *
     * @param exchange the HTTP server exchange for the response
     * @return a new BsonResponse instance
     */
    public static BsonResponse init(HttpServerExchange exchange) {
        return new BsonResponse(exchange);
    }

    /**
     * Factory method to retrieve or create a BsonResponse from an existing exchange.
     * <p>
     * This method retrieves an existing BsonResponse instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the BsonResponse associated with the exchange
     */
    public static BsonResponse of(HttpServerExchange exchange) {
        return BsonResponse.of(exchange, BsonResponse.class);
    }

    /**
     * Converts the BSON content to a JSON string for HTTP transmission.
     * <p>
     * This method serializes the internal BSON content to a JSON string format
     * that can be sent over HTTP. If no content has been set, returns null.
     * The conversion uses RESTHeart's BsonUtils to ensure proper JSON formatting
     * and MongoDB extended JSON support.
     * </p>
     *
     * @return the JSON string representation of the BSON content, or null if no content is set
     */
    @Override
    public String readContent() {
        if (content != null) {
            return toJson(content);
        } else {
            return null;
        }
    }

    /**
     * Sets the response content using a BSON ArrayBuilder.
     * <p>
     * This convenience method allows setting the response content directly from
     * a BsonUtils ArrayBuilder, automatically extracting the built BsonArray.
     * This is useful when constructing complex array responses programmatically.
     * </p>
     *
     * @param builder the ArrayBuilder containing the BSON array content
     */
    public void setContent(ArrayBuilder builder) {
        setContent(builder.get());
    }

    /**
     * Sets the response content using a BSON DocumentBuilder.
     * <p>
     * This convenience method allows setting the response content directly from
     * a BsonUtils DocumentBuilder, automatically extracting the built BsonDocument.
     * This is useful when constructing complex document responses programmatically.
     * </p>
     *
     * @param builder the DocumentBuilder containing the BSON document content
     */
    public void setContent(DocumentBuilder builder) {
        setContent(builder.get());
    }

    /**
     * Sets the response in an error state with the specified status code and message.
     * <p>
     * This method configures the response for error conditions by setting the HTTP status code
     * and creating a standardized error response document. The error document contains a "msg"
     * field with the error message. If both a custom message and throwable are provided,
     * the throwable's message takes precedence.
     * </p>
     * <p>
     * The error response format follows the pattern:
     * <pre>
     * {
     *   "msg": "error message here"
     * }
     * </pre>
     * </p>
     *
     * @param code the HTTP status code to set (e.g., 400, 404, 500)
     * @param message the error message to include in the response, or null
     * @param t an optional throwable whose message will override the provided message
     */
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
