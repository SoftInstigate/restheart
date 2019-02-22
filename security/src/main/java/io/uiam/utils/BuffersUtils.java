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

import static io.uiam.handlers.ModificableContentSinkConduit.MAX_CONTENT_SIZE;
import io.undertow.connector.PooledByteBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
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
    public static ByteBuffer readByteBuffer(final PooledByteBuffer[] srcs)
            throws IOException {
        if (srcs == null) {
            return null;
        }

        ByteBuffer dst = ByteBuffer.allocate(MAX_CONTENT_SIZE);

        for (int i = 0; i < srcs.length; ++i) {
            PooledByteBuffer pooled = srcs[i];
            if (pooled != null) {
                final ByteBuffer buf = pooled.getBuffer();

                if (buf.remaining() > dst.remaining()) {
                    LOGGER.error("Request content exceeeded {} bytes limit",
                            MAX_CONTENT_SIZE);
                    throw new IOException("Request content exceeeded "
                            + MAX_CONTENT_SIZE + " bytes limit");
                }

                if (buf.hasRemaining()) {
                    Buffers.copy(dst, buf);

                    // very important, I lost a day for this!
                    buf.flip();
                }
            }
        }

        return dst.flip();
    }
}
