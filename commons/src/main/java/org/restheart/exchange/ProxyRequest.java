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
import java.lang.reflect.Field;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Base class for Request implementations that support content buffering for proxy operations.
 * <p>
 * This abstract class extends Request and implements BufferedExchange to provide specialized
 * functionality for requests that need to be proxied to backend services. It manages content
 * buffering using Undertow's PooledByteBuffer system, allowing request content to be read
 * multiple times and forwarded to different destinations.
 * </p>
 * <p>
 * ProxyRequest is essential for scenarios where:
 * <ul>
 *   <li>Request content needs to be inspected before forwarding</li>
 *   <li>Content transformation is required during proxying</li>
 *   <li>Multiple backend services need to receive the same content</li>
 *   <li>Content validation must occur before forwarding</li>
 *   <li>Logging or auditing of request content is needed</li>
 * </ul>
 * </p>
 * <p>
 * The class stores buffered content in the BUFFERED_REQUEST_DATA attachment of the
 * HttpServerExchange, ensuring proper integration with Undertow's request processing
 * pipeline. It also implements AutoCloseable to ensure proper cleanup of buffer
 * resources and prevent memory leaks.
 * </p>
 * <p>
 * Subclasses must implement {@link #readContent()} and {@link #writeContent(Object)}
 * to handle the specific content type conversion between the generic type T and
 * the underlying byte buffer representation.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> the type of content handled by this proxy request (e.g., String, JsonElement, byte[])
 */
public abstract class ProxyRequest<T> extends Request<T> implements BufferedExchange<T>, AutoCloseable {
    /**
     * Constructs a new ProxyRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by subclasses.
     * It initializes the proxy request with buffering capabilities enabled
     * for content that may need to be forwarded to backend services.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    public ProxyRequest(HttpServerExchange exchange) {
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
     * Retrieves the attachment key for buffered request data using reflection.
     * <p>
     * This method accesses Undertow's internal BUFFERED_REQUEST_DATA field through
     * reflection to obtain the attachment key used for storing buffered content.
     * This approach ensures compatibility with Undertow's internal buffering
     * mechanism while providing access to the buffered data for proxy operations.
     * </p>
     * <p>
     * The method uses reflection because BUFFERED_REQUEST_DATA is a private field
     * in HttpServerExchange that is not exposed through the public API.
     * </p>
     *
     * @return the attachment key for accessing buffered request data
     * @throws RuntimeException if the BUFFERED_REQUEST_DATA field cannot be found or accessed
     */
    @SuppressWarnings("unchecked")
    protected AttachmentKey<PooledByteBuffer[]> getRawContentKey() {
        Field f;

        try {
            f = HttpServerExchange.class.getDeclaredField("BUFFERED_REQUEST_DATA");
            f.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException ex) {
            throw new RuntimeException("could not find BUFFERED_REQUEST_DATA field", ex);
        }

        try {
            return (AttachmentKey<PooledByteBuffer[]>) f.get(getWrappedExchange());
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException("could not access BUFFERED_REQUEST_DATA field", ex);
        }
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
        var oldBuffers = getWrappedExchange().getAttachment(getRawContentKey());
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
     * Returns the underlying PooledByteBuffer array containing the buffered content.
     * <p>
     * This method provides direct access to the internal buffer representation.
     * The returned array may contain multiple buffers if the content is large
     * enough to span multiple buffer segments.
     * </p>
     * <p>
     * Content must be explicitly made available through request interceptors
     * that specify {@code requiresContent = true} in their @RegisterPlugin
     * annotation. This ensures that the request content is properly buffered
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
            throw new IllegalStateException("Request content is not available. "
                    + "Add a Request Inteceptor with "
                    + "@RegisterPlugin(requiresContent = true) to make "
                    + "the content available.");
        }

        return getWrappedExchange().getAttachment(getRawContentKey());
    }

    /**
     * Checks whether content is available in the buffer for reading.
     * <p>
     * This method provides a quick way to determine if the buffer contains
     * content that can be read via {@link #readContent()}. It returns true
     * if there is buffered content available, false otherwise.
     * </p>
     * <p>
     * Content availability depends on request interceptors being configured
     * with requiresContent = true, which triggers the buffering of request
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
     * After calling this method, the proxy request should not be used for
     * further content operations as the underlying buffers will be released
     * back to the pool.
     * </p>
     */
    @Override
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
