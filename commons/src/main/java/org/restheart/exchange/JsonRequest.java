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

import org.restheart.utils.ChannelReader;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.undertow.server.HttpServerExchange;

/**
 * ServiceRequest implementation that handles JSON content using Google's Gson library.
 * <p>
 * This class provides specialized handling for JSON content in HTTP requests,
 * extending ServiceRequest to offer efficient JSON parsing and validation
 * capabilities. It uses Gson's JsonParser for parsing and implements
 * RawBodyAccessor to provide access to both parsed JSON and raw string content.
 * </p>
 * <p>
 * JsonRequest is commonly used by services that need to process JSON payloads
 * such as API endpoints, data transformation services, or any handler that
 * works with structured JSON data. The class automatically parses incoming
 * JSON content and provides convenient access methods.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Efficient JSON parsing using Gson library</li>
 *   <li>Access to both parsed JsonElement and raw JSON string</li>
 *   <li>Comprehensive error handling for malformed JSON</li>
 *   <li>Content-Length validation before parsing</li>
 *   <li>Memory-efficient processing for JSON payloads</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonRequest extends ServiceRequest<JsonElement> implements RawBodyAccessor<String> {

    /** The raw JSON content from the request body before parsing. */
    private String rawBody;

    /**
     * Constructs a new JsonRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected JsonRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a new JsonRequest instance.
     * <p>
     * This method creates a fresh JsonRequest instance for the given exchange.
     * The content will be parsed when {@link #parseContent()} is called.
     * </p>
     *
     * @param exchange the HTTP server exchange containing the JSON request
     * @return a new JsonRequest instance
     */
    public static JsonRequest init(HttpServerExchange exchange) {
        return new JsonRequest(exchange);
    }

    /**
     * Factory method to retrieve or create a JsonRequest from an existing exchange.
     * <p>
     * This method retrieves an existing JsonRequest instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the JsonRequest associated with the exchange
     */
    public static JsonRequest of(HttpServerExchange exchange) {
        return of(exchange, JsonRequest.class);
    }

    /**
     * Parses the JSON content from the request body into a JsonElement.
     * <p>
     * This method reads the raw JSON content from the request body and converts
     * it into a JsonElement using Gson's JsonParser. The raw content is also
     * stored internally for access via {@link #getRawBody()}.
     * </p>
     * <p>
     * The parsing process:
     * <ol>
     *   <li>Checks the Content-Length header to determine if content exists</li>
     *   <li>Reads the raw JSON string from the request channel if content is present</li>
     *   <li>Parses the JSON string into a JsonElement using Gson</li>
     *   <li>Returns null if no content is available (Content-Length is 0 or negative)</li>
     * </ol>
     * </p>
     * <p>
     * The parsing supports all valid JSON structures including objects, arrays,
     * primitives, and null values. If the JSON is malformed, a BadRequestException
     * is thrown with details about the parsing error.
     * </p>
     *
     * @return the parsed JSON content as a JsonElement, or null if no content is available
     * @throws IOException if there is an error reading the request body
     * @throws BadRequestException if the JSON content is malformed or cannot be parsed
     */
    @Override
    public JsonElement parseContent() throws IOException, BadRequestException {
        if (wrapped.getRequestContentLength() > 0) {
            try {
                rawBody = ChannelReader.readString(wrapped);
                return JsonParser.parseString(rawBody);
            } catch(JsonSyntaxException jse) {
                throw new BadRequestException(jse.getMessage(), jse);
            }
        } else {
            return null;
        }
    }

    /**
     * Returns the raw JSON content from the request body before parsing.
     * <p>
     * This method provides access to the original JSON string that was received
     * in the request body. The raw content is available after {@link #parseContent()}
     * has been called. This can be useful for logging, debugging, caching, or
     * when the original JSON format needs to be preserved.
     * </p>
     *
     * @return the raw JSON content as a string, or null if content hasn't been parsed yet
     */
    @Override
    public String getRawBody() {
        return rawBody;
    }
}
