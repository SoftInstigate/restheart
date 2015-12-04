/*
 * RESTHeart - the Web API for MongoDB
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
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

        int read = Channels.readBlocking(channel, buf);
        
        while (read != -1) {
            buf.flip();
            os.write(buf.array(), 0, read);
            buf.clear();
            
            read = Channels.readBlocking(channel, buf);
        }
        
        String ret = os.toString(CHARSET.name());
        
        return ret;
    }

    private ChannelReader() {
    }
}
