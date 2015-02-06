/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ChannelReader {

    final static Charset charset = Charset.forName("utf-8");

    /**
     *
     * @param channel
     * @return
     * @throws IOException
     */
    public static String read(StreamSourceChannel channel) throws IOException {
        StringBuilder content = new StringBuilder();

        ByteBuffer buf = ByteBuffer.allocate(128);

        while (Channels.readBlocking(channel, buf) != -1) {
            buf.flip();
            content.append(charset.decode(buf));
            buf.clear();
        }

        return content.toString();
    }
}
