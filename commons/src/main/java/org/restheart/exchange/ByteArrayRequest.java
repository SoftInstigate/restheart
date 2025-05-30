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
import java.nio.charset.StandardCharsets;

import org.restheart.utils.ChannelReader;

import io.undertow.server.HttpServerExchange;

/**
 * ServiceRequest implementation that handles binary content as byte arrays.
 * <p>
 * This class provides a specialized request handler for processing binary data
 * such as images, files, documents, or other non-text content. It extends
 * ServiceRequest to provide efficient handling of byte array content from
 * HTTP request bodies.
 * </p>
 * <p>
 * ByteArrayRequest is commonly used by services that need to process binary
 * uploads, file attachments, or any content that should be treated as raw
 * bytes rather than parsed text or JSON. The class efficiently reads the
 * entire request body into memory as a byte array.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Efficient binary content reading from HTTP request streams</li>
 *   <li>Automatic handling of Content-Length for proper data reading</li>
 *   <li>Convenient string conversion method for debugging</li>
 *   <li>Memory-efficient processing for binary data</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayRequest extends ServiceRequest<byte[]> {
    /**
     * Constructs a new ByteArrayRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected ByteArrayRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a new ByteArrayRequest instance.
     * <p>
     * This method creates a fresh ByteArrayRequest instance for the given exchange.
     * The content will be parsed when {@link #parseContent()} is called.
     * </p>
     *
     * @param exchange the HTTP server exchange containing the binary request
     * @return a new ByteArrayRequest instance
     */
    public static ByteArrayRequest init(HttpServerExchange exchange) {
        return new ByteArrayRequest(exchange);
    }

    /**
     * Factory method to retrieve or create a ByteArrayRequest from an existing exchange.
     * <p>
     * This method retrieves an existing ByteArrayRequest instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the ByteArrayRequest associated with the exchange
     */
    public static ByteArrayRequest of(HttpServerExchange exchange) {
        return of(exchange, ByteArrayRequest.class);
    }

    /**
     * Parses the binary content from the request body into a byte array.
     * <p>
     * This method reads the raw binary content from the request body and returns
     * it as a byte array. The method checks the Content-Length header to determine
     * if there is content to read, and returns an empty byte array if no content
     * is present.
     * </p>
     * <p>
     * The parsing process:
     * <ol>
     *   <li>Checks the Content-Length header to determine if content exists</li>
     *   <li>Reads all available bytes from the request channel if content is present</li>
     *   <li>Returns an empty byte array if no content is available</li>
     * </ol>
     * </p>
     * <p>
     * This method is suitable for reading binary files, images, documents, or any
     * other binary data sent in the request body. For very large files, consider
     * memory usage implications as the entire content is loaded into memory.
     * </p>
     *
     * @return the complete binary content as a byte array, or an empty array if no content
     * @throws IOException if there is an error reading from the request channel
     * @throws BadRequestException if the request content is malformed or cannot be processed
     */
    @Override
    public byte[] parseContent() throws IOException, BadRequestException {
        if (wrapped.getRequestContentLength() > 0) {
            return ChannelReader.readBytes(wrapped);
        } else {
            return new byte[0];
        }
    }

    /**
     * Converts the binary content to a UTF-8 string representation.
     * <p>
     * This convenience method interprets the byte array content as UTF-8 encoded
     * text and returns it as a String. This is useful for debugging, logging,
     * or when the binary content is known to contain text data.
     * </p>
     * <p>
     * <strong>Note:</strong> This method assumes the binary content represents
     * valid UTF-8 text. If the content contains non-text binary data, the
     * resulting string may contain invalid characters or be unreadable.
     * </p>
     * <p>
     * The method will return null if no content has been parsed yet, or an
     * empty string if the content is an empty byte array.
     * </p>
     *
     * @return the binary content interpreted as a UTF-8 string, or null if no content is available
     */
    public String getContentString() {
        return new String(getContent(), StandardCharsets.UTF_8);
    }
}
