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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.restheart.utils.BuffersUtils;

/**
 * ProxyResponse implementation that handles JSON content for proxy operations.
 * <p>
 * This class provides specialized handling for JSON content in proxy response scenarios,
 * extending ProxyResponse to offer efficient JSON parsing, validation, and
 * manipulation capabilities. It uses Google's Gson library for JSON processing
 * and supports buffering for content that needs to be sent back to clients.
 * </p>
 * <p>
 * JsonProxyResponse is commonly used in proxy scenarios where JSON content
 * received from backend services needs to be inspected, transformed, or validated
 * before being forwarded to clients. The class provides efficient memory management
 * through Undertow's PooledByteBuffer system while offering convenient JSON access methods.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Efficient JSON parsing with error handling and validation</li>
 *   <li>Content buffering for proxy operations</li>
 *   <li>Automatic Content-Type header management</li>
 *   <li>Structured error response generation in JSON format</li>
 *   <li>Memory-efficient handling of large JSON payloads</li>
 *   <li>Support for null and empty content scenarios</li>
 *   <li>Stack trace inclusion support for debugging</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonProxyResponse extends ProxyResponse<JsonElement> {
    /**
     * Constructs a new JsonProxyResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #of(HttpServerExchange)} to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected JsonProxyResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a JsonProxyResponse from an HTTP exchange.
     * <p>
     * This method creates a new JsonProxyResponse instance for handling
     * JSON content in proxy response scenarios. The response will be configured
     * to handle JsonElement content efficiently with proper buffering support.
     * </p>
     *
     * @param exchange the HTTP server exchange for the JSON response
     * @return a new JsonProxyResponse instance
     */
    public static JsonProxyResponse of(HttpServerExchange exchange) {
        return new JsonProxyResponse(exchange);
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
     *   <li>Throws IOException with details if parsing fails</li>
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
                throw new IOException("Error parsing json", ex);
            }
        }
    }

    /**
     * Updates the response content with the provided JsonElement.
     * <p>
     * This method replaces the current buffered content with the provided JsonElement,
     * converting it to its JSON string representation and storing it efficiently in
     * PooledByteBuffer segments. The method automatically sets the appropriate
     * Content-Type header to "application/json".
     * </p>
     * <p>
     * The method handles null content by clearing the buffer. For non-null content,
     * it serializes the JsonElement to its JSON string representation and stores
     * it in the buffer system. Unlike the request counterpart, this method does not
     * update Content-Length headers as that is handled by Undertow's response
     * processing pipeline.
     * </p>
     * <p>
     * <strong>Important:</strong> This method allocates PooledByteBuffer resources
     * that must be properly released. Ensure that {@code close()} is called on
     * the response to avoid memory leaks.
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
        } else {
            PooledByteBuffer[] dest;
            if (isContentAvailable()) {
                dest = getBuffer();
            } else {
                dest = new PooledByteBuffer[MAX_BUFFERS];
                setBuffer(dest);
            }

            BuffersUtils.transfer(ByteBuffer.wrap(content.toString().getBytes()), dest, wrapped);
        }
    }

    /**
     * Generates structured error content in JSON format as a JsonElement.
     * <p>
     * This method creates a standardized JSON error response that includes
     * the HTTP status code, status description, error message, and optionally
     * exception details with stack traces. The generated JSON is properly
     * formatted and escaped for safe transmission.
     * </p>
     * <p>
     * The error response structure includes:
     * <ul>
     *   <li>HTTP status code and description</li>
     *   <li>Custom error message (if provided)</li>
     *   <li>Exception class name (if throwable provided)</li>
     *   <li>Stack trace (if enabled and throwable provided)</li>
     * </ul>
     * </p>
     *
     * @param code the HTTP status code for the error
     * @param httpStatusText the HTTP status text description
     * @param message custom error message, or null if not applicable
     * @param t the throwable that caused the error, or null if not applicable
     * @param includeStackTrace whether to include the full stack trace in the response
     * @return the JSON error content as a JsonElement
     * @throws IOException if there is an error generating the JSON content
     */
    @Override
    protected JsonElement getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) throws IOException {
        var resp = new JsonObject();
        resp.add("http status code", new JsonPrimitive(code));
        resp.add("http status description", new JsonPrimitive(httpStatusText));
        if (message != null) {
            resp.add("message", new JsonPrimitive(avoidEscapedChars(message)));
        }
        var nrep = new JsonObject();
        if (t != null) {
            nrep.add("class", new JsonPrimitive(t.getClass().getName()));
            if (includeStackTrace) {
                JsonArray stackTrace = getStackTrace(t);
                if (stackTrace != null) {
                    nrep.add("stack trace", stackTrace);
                }
            }
            resp.add("exception", nrep);
        }
        return resp;
    }

    /**
     * Sanitizes string content to avoid JSON escaping issues.
     * <p>
     * This method replaces potentially problematic characters in strings
     * that will be included in JSON responses. It converts double quotes
     * to single quotes and tabs to spaces to prevent JSON parsing issues.
     * </p>
     *
     * @param s the string to sanitize, or null
     * @return the sanitized string, or null if input was null
     */
    private static String avoidEscapedChars(String s) {
        return s == null ? null : s.replaceAll("\"", "'").replaceAll("\t", "  ");
    }

    /**
     * Converts a throwable's stack trace to a JSON array format.
     * <p>
     * This method extracts the complete stack trace from a throwable and
     * converts it into a JSON array where each line of the stack trace
     * becomes a separate array element. The content is sanitized to prevent
     * JSON formatting issues.
     * </p>
     *
     * @param t the throwable to extract stack trace from, or null
     * @return a JSON array containing the stack trace lines, or null if no stack trace available
     */
    private static JsonArray getStackTrace(Throwable t) {
        if (t == null || t.getStackTrace() == null) {
            return null;
        }

        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        var st = avoidEscapedChars(sw.toString());
        var lines = st.split("\n");
        var list = new JsonArray();
        for (String line : lines) {
            list.add(new JsonPrimitive(line));
        }
        return list;
    }
}
