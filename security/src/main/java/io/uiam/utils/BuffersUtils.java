/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.utils;

import static io.uiam.handlers.exchange.AbstractExchange.MAX_CONTENT_SIZE;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Buffers;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BuffersUtils {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(BuffersUtils.class);

    /**
     * @param srcs
     * @return
     * @throws IOException
     */
    public static ByteBuffer toByteBuffer(final PooledByteBuffer[] srcs)
            throws IOException {
        if (srcs == null) {
            return null;
        }

        ByteBuffer dst = ByteBuffer.allocate(MAX_CONTENT_SIZE);

        for (int i = 0; i < srcs.length; i++) {
            PooledByteBuffer src = srcs[i];
            if (src != null) {
                final ByteBuffer srcBuffer = src.getBuffer();

                if (srcBuffer.remaining() > dst.remaining()) {
                    LOGGER.error("Request content exceeeded {} bytes limit",
                            MAX_CONTENT_SIZE);
                    throw new IOException("Request content exceeeded "
                            + MAX_CONTENT_SIZE + " bytes limit");
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

    public static byte[] toByteArray(final PooledByteBuffer[] srcs)
            throws IOException {
        ByteBuffer content = toByteBuffer(srcs);

        byte[] ret = new byte[content.limit()];

        content.get(ret);

        return ret;
    }

    public static String toString(final PooledByteBuffer[] srcs, Charset cs)
            throws IOException {
        return new String(toByteArray(srcs), cs);
    }
    
    public static String toString(final byte[] src, Charset cs)
            throws IOException {
        return new String(src, cs);
    }

    /**
     * transfer the src data to the pooled buffers overwriting the exising data
     *
     * @param src
     * @param dest
     * @param exchange
     * @return
     */
    public static int transfer(final ByteBuffer src,
            final PooledByteBuffer[] dest,
            HttpServerExchange exchange) {
        int copied = 0;
        int pidx = 0;

        //src.rewind();
        while (src.hasRemaining() && pidx < dest.length) {
            ByteBuffer _dest;

            if (dest[pidx] == null) {
                dest[pidx] = exchange.getConnection()
                        .getByteBufferPool().allocate();
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
        
        
        return copied;
    }

    public static void dump(String msg, PooledByteBuffer[] data) {
        int nbuf = 0;
        for (PooledByteBuffer dest : data) {
            if (dest != null) {
                ByteBuffer src = dest.getBuffer();
                StringBuilder sb = new StringBuilder();

                try {
                    Buffers.dump(src, sb, 2, 2);
                    LOGGER.debug("{} buffer #{}:\n{}", msg, nbuf, sb);
                }
                catch (IOException ie) {
                    LOGGER.debug("failed to dump buffered content", ie);
                }
            }
            nbuf++;
        }
    }

    /**
     * append the src data to the pooled buffers
     *
     * @param src
     * @param dest
     * @param exchange
     * @return
     */
    public static int append(final ByteBuffer src,
            final PooledByteBuffer[] dest,
            HttpServerExchange exchange) {
        int copied = 0;
        int pidx = 0;

        src.rewind();
        while (src.hasRemaining() && pidx < dest.length) {
            ByteBuffer _dest;

            if (dest[pidx] == null) {
                dest[pidx] = exchange.getConnection()
                        .getByteBufferPool().allocate();
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

        return copied;
    }

    public static int transfer(final PooledByteBuffer[] src,
            final PooledByteBuffer[] dest,
            HttpServerExchange exchange) {
        int copied = 0;
        int idx = 0;

        while (idx < src.length && idx < dest.length) {
            if (src[idx] != null) {
                if (dest[idx] == null) {
                    dest[idx] = exchange.getConnection()
                            .getByteBufferPool().allocate();
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

        return copied;
    }
}
