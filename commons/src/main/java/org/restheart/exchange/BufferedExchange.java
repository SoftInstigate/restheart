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

import io.undertow.connector.PooledByteBuffer;

/**
 * Interface for exchanges that buffer content in memory for efficient access and proxying.
 * <p>
 * A buffered exchange stores content in Undertow's PooledByteBuffer system, which provides
 * efficient memory management for request/response content. This buffering mechanism enables
 * content to be read multiple times and supports proxying scenarios where content needs to
 * be forwarded to backend services.
 * </p>
 * <p>
 * The buffering approach is particularly useful for:
 * <ul>
 *   <li>Proxied requests where content must be forwarded to another service</li>
 *   <li>Content transformation scenarios requiring multiple passes over the data</li>
 *   <li>Logging or auditing where raw content access is needed</li>
 *   <li>Error handling where original content may need to be preserved</li>
 * </ul>
 * </p>
 * <p>
 * Implementations must handle the conversion between the generic type T and the underlying
 * byte buffer representation, ensuring proper encoding/decoding and resource management.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> the type of content handled by this buffered exchange (e.g., String, BsonValue, byte[])
 */
public interface BufferedExchange<T> {
    /**
     * Reads and converts the buffered content to the specified type.
     * <p>
     * This method retrieves data from the internal PooledByteBuffer and converts it
     * to the appropriate type T. The conversion process depends on the implementation
     * and may involve deserialization, character encoding, or other transformation
     * operations.
     * </p>
     * <p>
     * Multiple calls to this method should return consistent results as long as the
     * buffer content hasn't been modified. The method should handle cases where no
     * content is available or the buffer is empty.
     * </p>
     *
     * @return the converted content of type T, or null if no content is available
     * @throws IOException if there is an error reading from the buffer or during content conversion
     */
    public abstract T readContent() throws IOException;

    /**
     * Converts the specified content to bytes and writes it to the internal buffer.
     * <p>
     * This method takes content of type T, converts it to its byte representation,
     * and stores it in the PooledByteBuffer. The conversion process may involve
     * serialization, character encoding, or other transformation operations depending
     * on the content type.
     * </p>
     * <p>
     * Writing content may replace any existing buffered content. Implementations
     * should properly manage buffer resources and handle memory allocation efficiently.
     * </p>
     *
     * @param content the content to write to the buffer; may be null to clear the buffer
     * @throws IOException if there is an error during content conversion or writing to the buffer
     */
    public abstract void writeContent(T content) throws IOException;

    /**
     * Returns the underlying PooledByteBuffer array containing the buffered content.
     * <p>
     * This method provides direct access to the internal buffer representation.
     * The returned array may contain multiple buffers if the content is large
     * enough to span multiple buffer segments.
     * </p>
     * <p>
     * Callers should handle the returned buffers carefully to avoid memory leaks
     * and should not modify the buffer content directly unless they understand
     * the implications for other buffer users.
     * </p>
     *
     * @return an array of PooledByteBuffer containing the buffered content, or null if no buffer is set
     */
    public PooledByteBuffer[] getBuffer();

    /**
     * Sets the internal buffer to the specified PooledByteBuffer array.
     * <p>
     * This method allows direct assignment of buffer content, which is useful
     * for scenarios like proxying where buffers need to be transferred between
     * exchanges or when working with pre-existing buffer content.
     * </p>
     * <p>
     * Setting a new buffer may replace any existing buffered content. Implementations
     * should properly manage resource cleanup for any previously held buffers to
     * prevent memory leaks.
     * </p>
     *
     * @param raw the PooledByteBuffer array to use as the internal buffer; may be null to clear the buffer
     */
    public void setBuffer(PooledByteBuffer[] raw);

    /**
     * Checks whether content is available in the buffer for reading.
     * <p>
     * This method provides a quick way to determine if the buffer contains
     * content that can be read via {@link #readContent()}. It returns true
     * if there is buffered content available, false otherwise.
     * </p>
     * <p>
     * This check is useful for conditional processing and can help avoid
     * unnecessary read operations when no content is present.
     * </p>
     *
     * @return true if content is available in the buffer, false otherwise
     */
    public boolean isContentAvailable();
}
