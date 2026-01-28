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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.restheart.utils.BuffersUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * ProxyRequest implementation that handles JSON content for proxy operations.
 * <p>
 * This class provides specialized handling for JSON content in proxy scenarios,
 * extending ProxyRequest to offer efficient JSON parsing, validation, and
 * manipulation capabilities. It uses Google's Gson library for JSON processing
 * and supports buffering for content that needs to be forwarded to backend services.
 * </p>
 * <p>
 * JsonProxyRequest is commonly used in proxy scenarios where JSON content
 * needs to be inspected, transformed, or validated before being forwarded
 * to backend services. The class provides efficient memory management through
 * Undertow's PooledByteBuffer system while offering convenient JSON access methods.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Efficient JSON parsing with error handling and validation</li>
 *   <li>Content buffering for proxy operations</li>
 *   <li>Automatic Content-Type and Content-Length header management</li>
 *   <li>Memory-efficient handling of large JSON payloads</li>
 *   <li>Support for null and empty content scenarios</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonProxyRequest extends ProxyRequest<JsonElement> {
    /**
     * Constructs a new JsonProxyRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #of(HttpServerExchange)} to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected JsonProxyRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a JsonProxyRequest from an HTTP exchange.
     * <p>
     * This method creates a new JsonProxyRequest instance for handling
     * JSON content in proxy scenarios. The request will be configured to
     * handle JsonElement content efficiently with proper buffering support.
     * </p>
     *
     * @param exchange the HTTP server exchange containing the JSON request
     * @return a new JsonProxyRequest instance
     */
    public static JsonProxyRequest of(HttpServerExchange exchange) {
        return new JsonProxyRequest(exchange);
    }

    /**
     * Reads the buffered JSON content and parses it into a JsonElement.
     * <p>
     * This method retrieves the JSON content from the internal PooledByteBuffer
     * and parses it using Gson's JsonParser. The method handles various content
     * states including null content, empty content, and malformed JSON.
     * </p>
     * <p>
     * The parsing process:
     * <ol>
     *   <li>Checks if content is available in the buffer</li>
     *   <li>Returns null if no content is buffered</li>
     *   <li>Returns JsonNull.INSTANCE if content is empty</li>
     *   <li>Parses the buffered content as JSON using UTF-8 encoding</li>
     *   <li>Provides detailed error information if parsing fails</li>
     * </ol>
     * </p>
     *
     * @return the parsed JSON content as a JsonElement, JsonNull.INSTANCE for empty content, or null if no content available
     * @throws IOException if there is an error reading from the buffer or parsing the JSON content
     */
    @Override
    public JsonElement readContent() throws IOException {
        if (!isContentAvailable()) {
            return null;
        }

        if (getWrappedExchange().getAttachment(getRawContentKey()) == null) {
            return JsonNull.INSTANCE;
        } else {
            try {
                return JsonParser.parseString(BuffersUtils.toString(getBuffer(), StandardCharsets.UTF_8));
            } catch (JsonParseException ex) {
                // dump bufferd content
                BuffersUtils.dump("Error parsing content", getBuffer());

                throw new IOException("Error parsing json", ex);
            }
        }
    }

    /**
     * Updates the request content with the provided JsonElement.
     * <p>
     * This method replaces the current buffered content with the provided JsonElement,
     * converting it to its JSON string representation and storing it efficiently in
     * PooledByteBuffer segments. The method automatically sets the appropriate
     * Content-Type header to "application/json" and updates the Content-Length header.
     * </p>
     * <p>
     * The method handles null content by clearing the buffer and removing the
     * Content-Length header. For non-null content, it serializes the JsonElement
     * to its JSON string representation and stores it in the buffer system.
     * </p>
     * <p>
     * <strong>Important:</strong> This method allocates PooledByteBuffer resources
     * that must be properly released. Ensure that {@code close()} is called on
     * the request to avoid memory leaks.
     * </p>
     *
     * @param content the JSON content to write, or null to clear the current content
     * @throws IOException if there is an error during JSON serialization or buffer operations
     */
    @Override
    public void writeContent(JsonElement content) throws IOException {
        setContentTypeAsJson();
        if (content == null) {
            setBuffer(null);
            getHeaders().remove(Headers.CONTENT_LENGTH);
        } else {
            PooledByteBuffer[] dest;
            if (isContentAvailable()) {
                dest = getBuffer();
            } else {
                dest = new PooledByteBuffer[MAX_BUFFERS];
                setBuffer(dest);
            }

            int copied = BuffersUtils.transfer(ByteBuffer.wrap(content.toString().getBytes()), dest, wrapped);

            // updated request content length
            // this is not needed in Response.writeContent() since done
            // by ModificableContentSinkConduit.updateContentLenght();
            getHeaders().put(Headers.CONTENT_LENGTH, copied);
        }
    }
}
