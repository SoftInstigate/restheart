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

import org.restheart.utils.GsonUtils.ObjectBuilder;
import org.restheart.utils.GsonUtils.ArrayBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;

/**
 * ServiceResponse implementation that handles JSON content using Google's Gson library.
 * <p>
 * This class provides specialized handling for JSON content in HTTP responses,
 * extending ServiceResponse to offer efficient JSON serialization and validation
 * capabilities. It uses Gson's JsonElement for content representation and
 * automatically sets the appropriate content type for JSON responses.
 * </p>
 * <p>
 * JsonResponse is commonly used by services that need to return JSON payloads
 * such as API endpoints, data services, or any handler that works with
 * structured JSON data. The class provides convenient methods for setting
 * content using various JSON builders and handles error response formatting.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Automatic JSON content type setting</li>
 *   <li>Support for JsonElement content representation</li>
 *   <li>Convenient builder pattern support for JSON construction</li>
 *   <li>Structured error response generation</li>
 *   <li>Efficient JSON serialization using Gson</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonResponse extends ServiceResponse<JsonElement> {
    /**
     * Constructs a new JsonResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. It automatically sets the response content type to JSON.
     * Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected JsonResponse(HttpServerExchange exchange) {
        super(exchange);
        setContentTypeAsJson();
    }

    /**
     * Factory method to create a new JsonResponse instance.
     * <p>
     * This method creates a fresh JsonResponse instance for the given exchange
     * with the content type automatically set to JSON. The response will be
     * configured to handle JsonElement content efficiently.
     * </p>
     *
     * @param exchange the HTTP server exchange for the JSON response
     * @return a new JsonResponse instance
     */
    public static JsonResponse init(HttpServerExchange exchange) {
        return new JsonResponse(exchange);
    }

    /**
     * Factory method to retrieve or create a JsonResponse from an existing exchange.
     * <p>
     * This method retrieves an existing JsonResponse instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * This allows for efficient reuse of response objects within the same request
     * processing pipeline.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the JsonResponse associated with the exchange
     */
    public static JsonResponse of(HttpServerExchange exchange) {
        return JsonResponse.of(exchange, JsonResponse.class);
    }

    /**
     * Converts the JSON content to its string representation.
     * <p>
     * This method serializes the internal JsonElement content to a JSON string
     * that can be sent over HTTP. The conversion uses Gson's default JSON
     * formatting. If no content has been set, returns null.
     * </p>
     *
     * @return the JSON string representation of the content, or null if no content is set
     */
    @Override
    public String readContent() {
        if (content != null) {
            return content.toString();
        } else {
            return null;
        }
    }

    /**
     * Sets the response content using a JSON ObjectBuilder.
     * <p>
     * This convenience method allows setting the response content directly from
     * a GsonUtils ObjectBuilder, automatically extracting the built JsonObject.
     * This is useful when constructing complex JSON objects programmatically
     * using the builder pattern.
     * </p>
     *
     * @param builder the ObjectBuilder containing the JSON object content, or null to clear content
     */
    public void setContent(ObjectBuilder builder) {
        if (builder != null) {
            setContent(builder.get());
        }
    }

    /**
     * Sets the response content using a JSON ArrayBuilder.
     * <p>
     * This convenience method allows setting the response content directly from
     * a GsonUtils ArrayBuilder, automatically extracting the built JsonArray.
     * This is useful when constructing complex JSON arrays programmatically
     * using the builder pattern.
     * </p>
     *
     * @param builder the ArrayBuilder containing the JSON array content, or null to clear content
     */
    public void setContent(ArrayBuilder builder) {
        if (builder != null) {
            setContent(builder.get());
        }
    }

    /**
     * Sets the response in an error state with the specified status code and message.
     * <p>
     * This method configures the response for error conditions by setting the HTTP status code
     * and creating a standardized JSON error response. The error response follows a consistent
     * format with optional message and exception information.
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

        setContent(resp);
    }
}
