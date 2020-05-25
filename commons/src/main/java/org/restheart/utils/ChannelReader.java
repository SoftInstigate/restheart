/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChannelReader {

    final static Charset CHARSET = StandardCharsets.UTF_8;

    /**
     *
     * @param channel
     * @return
     * @throws IOException
     */
    public static String read(StreamSourceChannel channel) throws IOException {
        final int capacity = 1024;

        ByteArrayOutputStream os = new ByteArrayOutputStream(capacity);
        
        ByteBuffer buf = ByteBuffer.allocate(capacity);
        
        while (Channels.readBlocking(channel, buf) != -1) {
            buf.flip();
            os.write(buf.array(), 0, buf.remaining());
            buf.clear();
        }
        return new String(os.toByteArray(), CHARSET);
    }
}
