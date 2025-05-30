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
import java.lang.reflect.Type;
import java.nio.ByteBuffer;

import org.restheart.utils.BuffersUtils;

import com.google.common.reflect.TypeToken;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * ProxyRequest implementation that handles binary content as byte arrays.
 * <p>
 * This class provides a specialized request handler for proxying binary data
 * such as images, files, or other non-text content. It extends ProxyRequest
 * and implements buffering functionality to store and manipulate byte array
 * content efficiently.
 * </p>
 * <p>
 * ByteArrayProxyRequest is commonly used in proxy scenarios where binary
 * content needs to be forwarded to backend services without modification,
 * or where content inspection/transformation of binary data is required.
 * The class provides efficient memory management through Undertow's
 * PooledByteBuffer system.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Efficient binary content handling with minimal memory overhead</li>
 *   <li>Support for large binary files through buffer segmentation</li>
 *   <li>Automatic Content-Length header management</li>
 *   <li>Resource management to prevent memory leaks</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayProxyRequest extends ProxyRequest<byte[]>{
    /**
     * Constructs a new ByteArrayProxyRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #of(HttpServerExchange)} to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected ByteArrayProxyRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a ByteArrayProxyRequest from an HTTP exchange.
     * <p>
     * This method creates a new ByteArrayProxyRequest instance for handling
     * binary content in proxy scenarios. The request will be configured to
     * handle byte array content efficiently.
     * </p>
     *
     * @param exchange the HTTP server exchange containing the binary request
     * @return a new ByteArrayProxyRequest instance
     */
    public static ByteArrayProxyRequest of(HttpServerExchange exchange) {
        return new ByteArrayProxyRequest(exchange);
    }

    /** Type token for reflection and serialization support. */
    private static final Type _TYPE = new TypeToken<ByteArrayProxyRequest>(ByteArrayProxyRequest.class) {
        private static final long serialVersionUID = 7455691404944194247L;
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
     * The returned byte array contains the complete binary content of the request.
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
     * Updates the request content with the provided byte array.
     * <p>
     * This method replaces the current buffered content with the provided byte array.
     * The content is efficiently stored in PooledByteBuffer segments to handle
     * large binary files without excessive memory allocation.
     * </p>
     * <p>
     * The method automatically updates the Content-Length header to reflect the
     * size of the new content, ensuring proper HTTP protocol compliance.
     * </p>
     * <p>
     * <strong>Important:</strong> This method allocates PooledByteBuffer resources
     * that must be properly released. Ensure that {@code close()} is called on
     * the request to avoid memory leaks.
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

            int copied = BuffersUtils.transfer(ByteBuffer.wrap(content), dest, wrapped);

            // updated request content length
            // this is not needed in Response.writeContent() since done
            // by ModificableContentSinkConduit.updateContentLenght();
            getHeaders().put(Headers.CONTENT_LENGTH, copied);
        }
    }
}
