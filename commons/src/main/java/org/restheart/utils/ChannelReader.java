/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.xnio.channels.Channels;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChannelReader {

    final static Charset CHARSET = StandardCharsets.UTF_8;
    final static int CAPACITY = 1024;

    /**
     *
     * @param channel
     * @return
     * @throws IOException
     */
    public static String readString(HttpServerExchange exchange) throws IOException {
        return new String(readBytes(exchange), CHARSET);
    }

    /**
     *
     * @param channel
     * @return
     * @throws IOException
     */
    public static byte[] readBytes(HttpServerExchange exchange) throws IOException {
        var channel = exchange.getRequestChannel();

        if (channel == null) {
            return null;
        }

        try (var os = new ByteArrayOutputStream(CAPACITY)) {
            var pooledByteBuffer = exchange.getConnection().getByteBufferPool().getArrayBackedPool().allocate();
            var buffer = pooledByteBuffer.getBuffer();

            while (Channels.readBlocking(channel, buffer) != -1) {
                buffer.flip();
                os.write(buffer.array(), 0, buffer.remaining());
                buffer.clear();
            }

            return os.toByteArray();
        }
    }

    /**
     *
     * @param stream
     * @return
     * @throws IOException
     */
    // public static String read(InputStream stream) throws IOException {
    // return CharStreams.toString(new InputStreamReader(stream, CHARSET));
    // }
}
