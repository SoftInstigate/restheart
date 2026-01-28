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

import org.restheart.utils.HttpStatus;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;

/**
 * Base class for Response implementations that support content buffering for proxy operations.
 * <p>
 * This abstract class extends Response and implements BufferedExchange to provide specialized
 * functionality for responses that need to be processed in proxy scenarios. It manages content
 * buffering using Undertow's PooledByteBuffer system, allowing response content to be read
 * multiple times, transformed, or forwarded to different destinations.
 * </p>
 * <p>
 * ProxyResponse is essential for scenarios where:
 * <ul>
 *   <li>Response content needs to be inspected before forwarding to clients</li>
 *   <li>Content transformation is required during proxying</li>
 *   <li>Response validation must occur before client delivery</li>
 *   <li>Logging or auditing of response content is needed</li>
 *   <li>Error responses need to be generated with specific content types</li>
 * </ul>
 * </p>
 * <p>
 * The class stores buffered content in the BUFFERED_RESPONSE_DATA_KEY attachment of the
 * HttpServerExchange, ensuring proper integration with Undertow's response processing
 * pipeline. It also implements AutoCloseable to ensure proper cleanup of buffer
 * resources and prevent memory leaks.
 * </p>
 * <p>
 * Subclasses must implement {@link #readContent()}, {@link #writeContent(Object)}, and
 * {@link #getErrorContent(int, String, String, Throwable, boolean)} to handle the specific
 * content type conversion and error response formatting.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> the type of content handled by this proxy response (e.g., String, JsonElement, byte[])
 */
public abstract class ProxyResponse<T> extends Response<T> implements BufferedExchange<T>, AutoCloseable {
    /** Attachment key for storing buffered response data in the HttpServerExchange. */
    public static final AttachmentKey<PooledByteBuffer[]> BUFFERED_RESPONSE_DATA_KEY = AttachmentKey.create(PooledByteBuffer[].class);

    /**
     * Constructs a new ProxyResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by subclasses.
     * It initializes the proxy response with buffering capabilities enabled
     * for content that may need to be processed or forwarded.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected ProxyResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Reads and converts the buffered content to the specified type.
     * <p>
     * This method must be implemented by subclasses to handle the conversion
     * from the internal PooledByteBuffer representation to the specific
     * content type T. The implementation should handle cases where no
     * content is available or the buffer is empty.
     * </p>
     *
     * @return the converted content of type T, or null if no content is available
     * @throws IOException if there is an error reading from the buffer or during content conversion
     */
    @Override
    public abstract T readContent() throws IOException;

    /**
     * Converts the specified content to bytes and writes it to the internal buffer.
     * <p>
     * This method must be implemented by subclasses to handle the conversion
     * from the specific content type T to the internal PooledByteBuffer
     * representation. The implementation should properly manage buffer
     * resources and handle memory allocation efficiently.
     * </p>
     *
     * @param content the content to write to the buffer; may be null to clear the buffer
     * @throws IOException if there is an error during content conversion or writing to the buffer
     */
    @Override
    public abstract void writeContent(T content) throws IOException;

    /**
     * Returns the attachment key for accessing buffered response data.
     * <p>
     * This method provides access to the attachment key used for storing
     * buffered content in the HttpServerExchange. Unlike ProxyRequest,
     * this uses a public constant rather than reflection since response
     * buffering is managed explicitly by RESTHeart.
     * </p>
     *
     * @return the attachment key for accessing buffered response data
     */
    public AttachmentKey<PooledByteBuffer[]> getRawContentKey() {
        return BUFFERED_RESPONSE_DATA_KEY;
    }

    /**
     * Returns the underlying PooledByteBuffer array containing the buffered content.
     * <p>
     * This method provides direct access to the internal buffer representation.
     * The returned array may contain multiple buffers if the content is large
     * enough to span multiple buffer segments.
     * </p>
     * <p>
     * Content must be explicitly made available through response interceptors
     * that specify {@code requiresContent = true} in their @RegisterPlugin
     * annotation. This ensures that the response content is properly buffered
     * before proxy operations attempt to access it.
     * </p>
     *
     * @return an array of PooledByteBuffer containing the buffered content
     * @throws IllegalStateException if content is not available because no interceptor
     *         has been configured with requiresContent = true
     */
    @Override
    public PooledByteBuffer[] getBuffer() {
        if (!isContentAvailable()) {
            throw new IllegalStateException("Response content is not available. "
                    + "Add a Response Inteceptor with "
                    + "@RegisterPlugin(requiresContent = true) to make "
                    + "the content available.");
        }

        return getWrappedExchange().getAttachment(getRawContentKey());
    }

    /**
     * Sets the internal buffer to the specified PooledByteBuffer array.
     * <p>
     * This method replaces the current buffered content with the provided buffer array.
     * It properly manages resource cleanup by closing any existing buffers before
     * setting the new ones, preventing memory leaks from unreleased buffer resources.
     * </p>
     * <p>
     * Setting a new buffer may replace any existing buffered content. The method
     * ensures that previously allocated buffers are properly closed to prevent
     * memory leaks in Undertow's buffer pool system.
     * </p>
     *
     * @param raw the PooledByteBuffer array to use as the internal buffer; may be null to clear the buffer
     */
    @Override
    public void setBuffer(PooledByteBuffer[] raw) {
        // close the current buffer pool
        var oldBuffers = getWrappedExchange().getAttachment(ProxyResponse.BUFFERED_RESPONSE_DATA_KEY);
        if (oldBuffers != null) {
            for (var oldBuffer: oldBuffers) {
                if (oldBuffer != null) {
                    oldBuffer.close();
                }
            }
        }

        getWrappedExchange().putAttachment(getRawContentKey(), raw);
    }

    /**
     * Checks whether content is available in the buffer for reading.
     * <p>
     * This method provides a quick way to determine if the buffer contains
     * content that can be read via {@link #readContent()}. It returns true
     * if there is buffered content available, false otherwise.
     * </p>
     * <p>
     * Content availability depends on response interceptors being configured
     * with requiresContent = true, which triggers the buffering of response
     * content for later access by proxy operations.
     * </p>
     *
     * @return true if content is available in the buffer, false otherwise
     */
    @Override
    public boolean isContentAvailable() {
        return null != getWrappedExchange().getAttachment(getRawContentKey());
    }

    /**
     * Sets the Content-Length header for the response.
     * <p>
     * This method is typically called automatically when content is written
     * to ensure that the response includes the correct content length information
     * for proper HTTP protocol compliance.
     * </p>
     *
     * @param length the content length in bytes
     */
    protected void setContentLength(int length) {
        getHeaders().put(Headers.CONTENT_LENGTH, length);
    }

    /**
     * Sets the response in an error state with structured error content.
     * <p>
     * This method configures the response for error conditions by setting the HTTP status code,
     * content type to JSON, and generating structured error content using the abstract
     * {@link #getErrorContent(int, String, String, Throwable, boolean)} method. The error
     * content is automatically written to the response buffer.
     * </p>
     * <p>
     * The method ensures consistent error response formatting across different proxy
     * response implementations while allowing subclasses to customize the specific
     * error content structure.
     * </p>
     *
     * @param code the HTTP status code to set (e.g., 400, 404, 500)
     * @param message the error message to include in the response, or null
     * @param t an optional throwable that caused the error
     * @throws RuntimeException if there is an IOException while writing the error content
     */
    @Override
    public void setInError(int code, String message, Throwable t) {
        setStatusCode(code);
        setContentTypeAsJson();
        setInError(true);
        try {
            writeContent(getErrorContent(code, HttpStatus.getStatusText(code), message, t, false));
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Generates structured error content for error responses.
     * <p>
     * This abstract method must be implemented by subclasses to create error response
     * content in the appropriate format for the specific content type T. The error
     * content should include the HTTP status information, custom error messages,
     * and optionally exception details and stack traces.
     * </p>
     * <p>
     * Implementations should create well-structured error responses that provide
     * sufficient information for clients to understand and handle the error condition
     * appropriately. Common error response formats include JSON objects with fields
     * for status codes, messages, and exception details.
     * </p>
     *
     * @param code the HTTP status code for the error
     * @param httpStatusText the HTTP status text description
     * @param message custom error message, or null if not applicable
     * @param t the throwable that caused the error, or null if not applicable
     * @param includeStackTrace whether to include the full stack trace in the response
     * @return the structured error content in the appropriate format for type T
     * @throws IOException if there is an error generating the error content
     */
    protected abstract T getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) throws IOException;

    /**
     * Closes this resource, relinquishing any underlying buffer resources.
     * <p>
     * This method ensures proper cleanup of PooledByteBuffer resources by
     * closing all allocated buffers and clearing the buffer reference. It's
     * essential to call this method to prevent memory leaks in Undertow's
     * buffer pool system.
     * </p>
     * <p>
     * The method safely handles cases where:
     * <ul>
     *   <li>No content is available (no-op)</li>
     *   <li>Individual buffers in the array are null</li>
     *   <li>The buffer array itself is null</li>
     * </ul>
     * </p>
     * <p>
     * After calling this method, the proxy response should not be used for
     * further content operations as the underlying buffers will be released
     * back to the pool.
     * </p>
     */
    public void close() {
        if (isContentAvailable()) {
            for (var b: this.getBuffer()) {
                if (b != null) {
                    b.close();
                }
            }

            this.setBuffer(null);
        }
    }
}
