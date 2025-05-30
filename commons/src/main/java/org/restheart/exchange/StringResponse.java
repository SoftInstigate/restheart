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

import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;

/**
 * ServiceResponse implementation that handles plain text content as strings.
 * <p>
 * This class provides specialized handling for HTTP responses containing plain text
 * content, extending ServiceResponse to offer efficient string handling and formatting
 * capabilities. It manages string content and provides JSON error response formatting
 * when errors occur.
 * </p>
 * <p>
 * StringResponse is commonly used by services that need to return:
 * <ul>
 *   <li>Plain text responses for simple APIs</li>
 *   <li>Template-generated content (HTML, XML, etc.)</li>
 *   <li>Log outputs or status messages</li>
 *   <li>Configuration data or documentation</li>
 *   <li>Custom formatted data that doesn't require structured parsing</li>
 * </ul>
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Direct string content handling without additional formatting</li>
 *   <li>Automatic JSON error response generation</li>
 *   <li>Efficient content transmission for text-based responses</li>
 *   <li>Flexible content type support (defaults to text/plain)</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class StringResponse extends ServiceResponse<String> {
    /**
     * Constructs a new StringResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is private and should only be called by factory methods.
     * Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    private StringResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a new StringResponse instance.
     * <p>
     * This method creates a fresh StringResponse instance for the given exchange.
     * The response will be configured to handle string content efficiently.
     * </p>
     *
     * @param exchange the HTTP server exchange for the string response
     * @return a new StringResponse instance
     */
    public static StringResponse init(HttpServerExchange exchange) {
        return new StringResponse(exchange);
    }

    /**
     * Factory method to retrieve or create a StringResponse from an existing exchange.
     * <p>
     * This method retrieves an existing StringResponse instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * This allows for efficient reuse of response objects within the same request
     * processing pipeline.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the StringResponse associated with the exchange
     */
    public static StringResponse of(HttpServerExchange exchange) {
        return of(exchange, StringResponse.class);
    }

    /**
     * Returns the string content for HTTP transmission.
     * <p>
     * This method provides direct access to the string content that will be
     * sent to the client. Unlike other response types that may require
     * serialization or transformation, StringResponse simply returns the
     * content as-is, making it very efficient for plain text responses.
     * </p>
     *
     * @return the string content ready for HTTP transmission, or null if no content is set
     */
    @Override
    public String readContent() {
        if (content != null) {
            return content;
        } else {
            return null;
        }
    }

    /**
     * Sets the response in an error state with JSON-formatted error content.
     * <p>
     * This method configures the response for error conditions by setting the HTTP status code
     * and creating a standardized JSON error response. Even though this is a StringResponse,
     * error responses are formatted as JSON to provide structured error information that
     * clients can parse and handle consistently across different response types.
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
     * <p>
     * The method automatically sets the Content-Type to "application/json" for error
     * responses to ensure proper client interpretation of the structured error data.
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
        setContent(resp.toString());
    }
}
