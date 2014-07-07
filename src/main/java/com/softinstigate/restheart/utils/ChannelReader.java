/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 * @author uji
 */
public class ChannelReader
{
    final static Charset charset = Charset.forName("utf-8");

    public static String read(StreamSourceChannel channel) throws IOException
    {
        StringBuilder content = new StringBuilder();

        ByteBuffer buf = ByteBuffer.allocate(128);

        while (Channels.readBlocking(channel, buf) != -1)
        {
            buf.flip();
            content.append(charset.decode(buf));
            buf.clear();
        }
        
        return content.toString();
    }
}
