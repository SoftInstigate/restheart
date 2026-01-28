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

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import org.restheart.utils.BuffersUtils;

/**
 * ProxyResponse implementation that handles binary content as byte arrays.
 * <p>
 * This class provides a specialized response handler for proxying binary data
 * such as images, files, or other non-text content. It extends ProxyResponse
 * and implements buffering functionality to store and manipulate byte array
 * content efficiently for responses sent back to clients.
 * </p>
 * <p>
 * ByteArrayProxyResponse is commonly used in proxy scenarios where binary
 * content received from backend services needs to be forwarded to clients,
 * or where content inspection/transformation of binary response data is required.
 * The class provides efficient memory management through Undertow's
 * PooledByteBuffer system.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Efficient binary content handling with minimal memory overhead</li>
 *   <li>Support for large binary files through buffer segmentation</li>
 *   <li>Structured error response generation in JSON format</li>
 *   <li>Resource management to prevent memory leaks</li>
 *   <li>Stack trace inclusion support for debugging</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayProxyResponse extends ProxyResponse<byte[]> {
    /**
     * Constructs a new ByteArrayProxyResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #of(HttpServerExchange)} to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected ByteArrayProxyResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a ByteArrayProxyResponse from an HTTP exchange.
     * <p>
     * This method creates a new ByteArrayProxyResponse instance for handling
     * binary content in proxy response scenarios. The response will be configured
     * to handle byte array content efficiently.
     * </p>
     *
     * @param exchange the HTTP server exchange for the binary response
     * @return a new ByteArrayProxyResponse instance
     */
    public static ByteArrayProxyResponse of(HttpServerExchange exchange) {
        return new ByteArrayProxyResponse(exchange);
    }

    /** Type token for reflection and serialization support. */
    private static final Type _TYPE = new TypeToken<ByteArrayProxyResponse>(ByteArrayProxyResponse.class) {
        private static final long serialVersionUID = -6882416842030533004L;
    }.getType();

    /**
     * Returns the Type information for this class.
     * <p>
     * This method provides type information that can be used for reflection,
     * serialization, or other type-based operations. It returns a Type token
     * that preserves generic type information at runtime.
     * </p>
     *
     * @return the Type representation of this class
     */
    public static Type type() {
        return _TYPE;
    }

    /**
     * Reads the buffered binary content and converts it to a byte array.
     * <p>
     * This method retrieves the binary content from the internal PooledByteBuffer
     * and converts it to a byte array for easier manipulation. The conversion
     * is performed efficiently using BuffersUtils to minimize memory allocation
     * and copying operations.
     * </p>
     * <p>
     * The returned byte array contains the complete binary content of the response.
     * For large files, this may consume significant memory, so callers should
     * be mindful of memory usage patterns.
     * </p>
     *
     * @return the complete binary content as a byte array, or null if no content is buffered
     * @throws IOException if there is an error reading from the buffer or converting to byte array
     */
    @Override
    public byte[] readContent() throws IOException {
        return BuffersUtils.toByteArray(getBuffer());
    }

    /**
     * Updates the response content with the provided byte array.
     * <p>
     * This method replaces the current buffered content with the provided byte array.
     * The content is efficiently stored in PooledByteBuffer segments to handle
     * large binary files without excessive memory allocation.
     * </p>
     * <p>
     * Unlike the request counterpart, this method does not update Content-Length
     * headers as that is handled by Undertow's ModificableContentSinkConduit during
     * the response writing process.
     * </p>
     * <p>
     * <strong>Important:</strong> This method allocates PooledByteBuffer resources
     * that must be properly released. Ensure that {@code close()} is called on
     * the response to avoid memory leaks.
     * </p>
     *
     * @param content the binary content to write, or null to clear the current content
     * @throws IOException if there is an error during content conversion or buffer operations
     */
    @Override
    public void writeContent(byte[] content) throws IOException {
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

            BuffersUtils.transfer(ByteBuffer.wrap(content), dest, wrapped);
        }
    }

    /**
     * Generates structured error content in JSON format as a byte array.
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
     * @return the JSON error content as a byte array
     * @throws IOException if there is an error generating the JSON content
     */
    @Override
    protected byte[] getErrorContent(int code, String httpStatusText, String message, Throwable t, boolean includeStackTrace) throws IOException {
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
        return resp.toString().getBytes();
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
        var st = sw.toString();
        st = avoidEscapedChars(st);
        String[] lines = st.split("\n");
        var list = new JsonArray();
        for (var line : lines) {
            list.add(new JsonPrimitive(line));
        }
        return list;
    }
}
