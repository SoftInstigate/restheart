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

    private ChannelReader() {
    }
}
