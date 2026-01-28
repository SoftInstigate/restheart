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
package org.restheart.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.restheart.exchange.Exchange.MAX_CONTENT_SIZE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Buffers;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;

/**
 * Utility class for handling buffer operations and conversions.
 * Provides methods for converting between different buffer types, managing
 * pooled byte buffers, and performing buffer operations safely with size limits.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BuffersUtils {

    /** Logger instance for this class. */
    private static final Logger LOGGER = LoggerFactory.getLogger(BuffersUtils.class);

    /**
     * Converts an array of pooled byte buffers to a single ByteBuffer.
     * This method consolidates multiple pooled buffers into one continuous buffer,
     * respecting the maximum content size limit.
     *
     * @param srcs array of pooled byte buffers to convert
     * @return a ByteBuffer containing all the data from the source buffers, or null if srcs is null
     * @throws IOException if the total content exceeds the maximum allowed size
     */
    public static ByteBuffer toByteBuffer(final PooledByteBuffer[] srcs) throws IOException {
        if (srcs == null) {
            return null;
        }

        var dst = ByteBuffer.allocate(MAX_CONTENT_SIZE);

        for (var src : srcs) {
            if (src != null) {
                final var srcBuffer = src.getBuffer();

                if (srcBuffer.remaining() > dst.remaining()) {
                    LOGGER.error("Request content exceeeded {} bytes limit", MAX_CONTENT_SIZE);
                    throw new IOException("Request content exceeeded " + MAX_CONTENT_SIZE + " bytes limit");
                }

                if (srcBuffer.hasRemaining()) {
                    Buffers.copy(dst, srcBuffer);

                    // very important, I lost a day for this!
                    srcBuffer.flip();
                }
            }
        }

        return dst.flip();
    }

    /**
     * Converts an array of pooled byte buffers to a byte array.
     * This method first converts the pooled buffers to a ByteBuffer and then
     * extracts the data as a byte array.
     *
     * @param srcs array of pooled byte buffers to convert
     * @return a byte array containing all the data from the source buffers
     * @throws IOException if the conversion fails or content exceeds size limits
     */
    public static byte[] toByteArray(final PooledByteBuffer[] srcs) throws IOException {
        var content = toByteBuffer(srcs);

        var ret = new byte[content.limit()];

        content.get(ret);

        return ret;
    }

    /**
     * Converts an array of pooled byte buffers to a string using the specified charset.
     *
     * @param srcs array of pooled byte buffers to convert
     * @param cs the charset to use for string conversion
     * @return a string representation of the buffer content
     * @throws IOException if the conversion fails or content exceeds size limits
     */
    public static String toString(final PooledByteBuffer[] srcs, Charset cs) throws IOException {
        return new String(toByteArray(srcs), cs);
    }

    /**
     * Converts a byte array to a string using the specified charset.
     *
     * @param src the byte array to convert
     * @param cs the charset to use for string conversion
     * @return a string representation of the byte array content
     * @throws IOException if the conversion fails
     */
    public static String toString(final byte[] src, Charset cs) throws IOException {
        return new String(src, cs);
    }

    /**
     * Transfers data from a source ByteBuffer to pooled byte buffers, overwriting existing data.
     * This method allocates new pooled buffers as needed and clears existing ones before copying.
     *
     * @param src the source ByteBuffer containing data to transfer
     * @param dest array of pooled byte buffers to receive the data
     * @param exchange the HTTP server exchange for accessing the byte buffer pool
     * @return the number of bytes copied
     */
    public static int transfer(final ByteBuffer src, final PooledByteBuffer[] dest, HttpServerExchange exchange) {
        var byteBufferPool = exchange.getConnection().getByteBufferPool();
        int copied = 0;
        int pidx = 0;

        //src.rewind();
        while (src.hasRemaining() && pidx < dest.length) {
            ByteBuffer _dest;

            if (dest[pidx] == null) {
                dest[pidx] = byteBufferPool.allocate();
                _dest = dest[pidx].getBuffer();
            } else {
                _dest = dest[pidx].getBuffer();
                _dest.clear();
            }

            copied += Buffers.copy(_dest, src);

            // very important, I lost a day for this!
            _dest.flip();

            pidx++;
        }

        // clean up remaining destination buffers
        while (pidx < dest.length) {
            dest[pidx] = null;
            pidx++;
        }

        return copied;
    }

    /**
     * Dumps the content of pooled byte buffers to the debug log for debugging purposes.
     * This method logs the hexadecimal representation of each buffer's content.
     *
     * @param msg a message to include in the log output
     * @param data array of pooled byte buffers to dump
     */
    public static void dump(String msg, PooledByteBuffer[] data) {
        int nbuf = 0;
        for (PooledByteBuffer dest : data) {
            if (dest != null) {
                ByteBuffer src = dest.getBuffer();
                StringBuilder sb = new StringBuilder();

                try {
                    Buffers.dump(src, sb, 2, 2);
                    LOGGER.debug("{} buffer #{}:\n{}", msg, nbuf, sb);
                } catch (IOException ie) {
                    LOGGER.debug("failed to dump buffered content", ie);
                }
            }
            nbuf++;
        }
    }

    /**
     * Appends data from a source ByteBuffer to pooled byte buffers.
     * Unlike transfer(), this method appends to existing buffer content rather than overwriting it.
     *
     * @param src the source ByteBuffer containing data to append
     * @param dest array of pooled byte buffers to append data to
     * @param exchange the HTTP server exchange for accessing the byte buffer pool
     * @return the number of bytes copied
     */
    public static int append(final ByteBuffer src, final PooledByteBuffer[] dest, HttpServerExchange exchange) {
        var byteBufferPool = exchange.getConnection().getByteBufferPool();
        int copied = 0;
        int pidx = 0;

        src.rewind();
        while (src.hasRemaining() && pidx < dest.length) {
            ByteBuffer _dest;

            if (dest[pidx] == null) {
                dest[pidx] = byteBufferPool.allocate();
                _dest = dest[pidx].getBuffer();
            } else {
                _dest = dest[pidx].getBuffer();
                _dest.position(_dest.limit());
            }

            copied += Buffers.copy(_dest, src);

            // very important, I lost a day for this!
            _dest.flip();

            pidx++;
        }

        // clean up remaining destination buffers
        while (pidx < dest.length) {
            dest[pidx] = null;
            pidx++;
        }

        return copied;
    }

    /**
     * Transfers data from source pooled byte buffers to destination pooled byte buffers.
     * This method copies data between two arrays of pooled buffers, allocating destination
     * buffers as needed.
     *
     * @param src array of source pooled byte buffers
     * @param dest array of destination pooled byte buffers
     * @param exchange the HTTP server exchange for accessing the byte buffer pool
     * @return the number of bytes copied
     */
    public static int transfer(final PooledByteBuffer[] src, final PooledByteBuffer[] dest, final HttpServerExchange exchange) {
        var byteBufferPool = exchange.getConnection().getByteBufferPool();
        int copied = 0;
        int idx = 0;

        while (idx < src.length && idx < dest.length) {
            if (src[idx] != null) {
                if (dest[idx] == null) {
                    dest[idx] = byteBufferPool.allocate();
                }

                ByteBuffer _dest = dest[idx].getBuffer();
                ByteBuffer _src = src[idx].getBuffer();

                copied += Buffers.copy(_dest, _src);

                // very important, I lost a day for this!
                _dest.flip();
                _src.flip();
            }

            idx++;
        }

        // clean up remaining destination buffers
        while (idx < dest.length) {
            dest[idx] = null;
            idx++;
        }

        return copied;
    }
}
