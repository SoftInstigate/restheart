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

import java.io.IOException;

import org.restheart.utils.ChannelReader;

import io.undertow.server.HttpServerExchange;

/**
 * ServiceRequest implementation that handles plain text content as strings.
 * <p>
 * This class provides specialized handling for HTTP requests containing plain text
 * content, extending ServiceRequest to offer efficient string parsing and handling
 * capabilities. It reads the raw request body and converts it directly to a String
 * using the appropriate character encoding.
 * </p>
 * <p>
 * StringRequest is commonly used by services that need to process:
 * <ul>
 *   <li>Plain text content submissions</li>
 *   <li>Raw data that doesn't require structured parsing</li>
 *   <li>Template content or configuration data</li>
 *   <li>Log entries or message content</li>
 *   <li>Custom protocol payloads in text format</li>
 * </ul>
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Efficient string reading from HTTP request streams</li>
 *   <li>Automatic handling of Content-Length for proper data reading</li>
 *   <li>UTF-8 character encoding support</li>
 *   <li>Memory-efficient processing for text content</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class StringRequest extends ServiceRequest<String> {
    /**
     * Constructs a new StringRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is private and should only be called by factory methods.
     * Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    private StringRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a new StringRequest instance.
     * <p>
     * This method creates a fresh StringRequest instance for the given exchange.
     * The content will be parsed when {@link #parseContent()} is called.
     * </p>
     *
     * @param exchange the HTTP server exchange containing the string request
     * @return a new StringRequest instance
     */
    public static StringRequest init(HttpServerExchange exchange) {
        return new StringRequest(exchange);
    }

    /**
     * Factory method to retrieve or create a StringRequest from an existing exchange.
     * <p>
     * This method retrieves an existing StringRequest instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the StringRequest associated with the exchange
     */
    public static StringRequest of(HttpServerExchange exchange) {
        return of(exchange, StringRequest.class);
    }

    /**
     * Parses the text content from the request body into a String.
     * <p>
     * This method reads the raw text content from the request body and returns
     * it as a String using the appropriate character encoding (typically UTF-8).
     * The method checks the Content-Length header to determine if there is content
     * to read, and returns null if no content is present.
     * </p>
     * <p>
     * The parsing process:
     * <ol>
     *   <li>Checks the Content-Length header to determine if content exists</li>
     *   <li>Reads all available text from the request channel if content is present</li>
     *   <li>Returns null if no content is available (Content-Length is 0 or negative)</li>
     * </ol>
     * </p>
     * <p>
     * This method is suitable for reading plain text content, configuration data,
     * template content, or any other string-based data sent in the request body.
     * The content is read using the platform's default character encoding, which
     * is typically UTF-8.
     * </p>
     *
     * @return the complete text content as a String, or null if no content is available
     * @throws IOException if there is an error reading from the request channel
     * @throws BadRequestException if the request content is malformed or cannot be processed
     */
    @Override
    public String parseContent() throws IOException, BadRequestException {
        if (wrapped.getRequestContentLength() > 0) {
            return ChannelReader.readString(wrapped);
        } else {
            return null;
        }
    }
}
