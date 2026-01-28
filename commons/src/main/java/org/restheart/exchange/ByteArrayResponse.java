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

import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import java.nio.charset.StandardCharsets;

/**
 * ServiceResponse implementation that handles binary content as byte arrays.
 * <p>
 * This class provides a specialized response handler for sending binary data
 * such as images, files, documents, or other non-text content back to clients.
 * It extends ServiceResponse to provide efficient handling of byte array content
 * for HTTP responses.
 * </p>
 * <p>
 * ByteArrayResponse is commonly used by services that need to return binary
 * content, file downloads, generated images, or any content that should be
 * treated as raw bytes rather than text or JSON. The class provides convenient
 * methods for setting content from both byte arrays and strings.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Efficient binary content handling for HTTP responses</li>
 *   <li>UTF-8 string conversion for text-based binary content</li>
 *   <li>Structured JSON error response generation</li>
 *   <li>Convenient string-to-bytes content setting</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayResponse extends ServiceResponse<byte[]> {
    /**
     * Constructs a new ByteArrayResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected ByteArrayResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a new ByteArrayResponse instance.
     * <p>
     * This method creates a fresh ByteArrayResponse instance for the given exchange.
     * The response will be configured to handle byte array content efficiently.
     * </p>
     *
     * @param exchange the HTTP server exchange for the binary response
     * @return a new ByteArrayResponse instance
     */
    public static ByteArrayResponse init(HttpServerExchange exchange) {
        return new ByteArrayResponse(exchange);
    }

    /**
     * Factory method to retrieve or create a ByteArrayResponse from an existing exchange.
     * <p>
     * This method retrieves an existing ByteArrayResponse instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the ByteArrayResponse associated with the exchange
     */
    public static ByteArrayResponse of(HttpServerExchange exchange) {
        return of(exchange, ByteArrayResponse.class);
    }

    /**
     * Converts the binary content to a UTF-8 string representation.
     * <p>
     * This method interprets the byte array content as UTF-8 encoded text
     * and returns it as a String. This is useful for debugging, logging,
     * or when the binary content contains text data that needs to be
     * transmitted as a string response.
     * </p>
     * <p>
     * <strong>Note:</strong> This method assumes the binary content represents
     * valid UTF-8 text. If the content contains non-text binary data, the
     * resulting string may contain invalid characters or be unreadable.
     * </p>
     *
     * @return the binary content interpreted as a UTF-8 string, or null if no content is set
     */
    @Override
    public String readContent() {
        if (content != null) {
            return new String(content, StandardCharsets.UTF_8);
        } else {
            return null;
        }
    }

    /**
     * Sets the response in an error state with the specified status code and message.
     * <p>
     * This method configures the response for error conditions by setting the HTTP status code
     * and creating a standardized JSON error response. The error response is automatically
     * converted to bytes and the content type is set to JSON.
     * </p>
     * <p>
     * The error response format follows the pattern:
     * <pre>
     * {
     *   "msg": "error message here",
     *   "exception": "exception message here"
     * }
     * </pre>
     * </p>
     *
     * @param code the HTTP status code to set (e.g., 400, 404, 500)
     * @param message the error message to include in the response, or null
     * @param t an optional throwable whose message will be included in the response
     */
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

        setContentTypeAsJson();
        setContent(resp.toString().getBytes());
    }

    /**
     * Convenience method to set the response content from a string.
     * <p>
     * This method converts the provided string to a byte array using the default
     * charset (UTF-8) and sets it as the response content. This is useful when
     * the response content is text-based but needs to be handled as binary data.
     * </p>
     * <p>
     * If the provided content is null, the response content is cleared by setting
     * it to null.
     * </p>
     *
     * @param content the string content to set, or null to clear the content
     */
    public void setContent(String content) {
        if (content != null) {
            setContent(content.getBytes());
        } else {
            setContent((byte[])null);
        }
    }
}
