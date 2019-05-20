/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.handlers.exchange;

import static org.restheart.security.handlers.exchange.AbstractExchange.MAX_BUFFERS;
import org.restheart.security.utils.BuffersUtils;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayRequest extends Request<byte[]> {

    protected ByteArrayRequest(HttpServerExchange exchange) {
        super(exchange);
        LOGGER = LoggerFactory.getLogger(ByteArrayRequest.class);
    }
    
    public static ByteArrayRequest wrap(HttpServerExchange exchange) {
        return new ByteArrayRequest(exchange);
    }
    
    /**
     * @return the content as Json
     * @throws java.io.IOException
     */
    @Override
    public byte[] readContent()
            throws IOException {
        return BuffersUtils.toByteArray(getRawContent());
    }
    
    @Override
    public void writeContent(byte[] content) throws IOException {
        if (content == null) {
            setRawContent(null);
        } else {
            PooledByteBuffer[] dest;
            if (isContentAvailable()) {
                dest = getRawContent();
            } else {
                dest = new PooledByteBuffer[MAX_BUFFERS];
                setRawContent(dest);
            }
            
            int copied = BuffersUtils.transfer(
                    ByteBuffer.wrap(content.toString().getBytes()),
                    dest,
                    wrapped);
            
            // updated request content length
            // this is not needed in Response.writeContent() since done
            // by ModificableContentSinkConduit.updateContentLenght();
            getWrapped().getRequestHeaders().put(Headers.CONTENT_LENGTH, copied);
        }
    }
}
