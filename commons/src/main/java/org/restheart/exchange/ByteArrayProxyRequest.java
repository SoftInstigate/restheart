/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
package org.restheart.exchange;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;

import org.restheart.utils.BuffersUtils;

import com.google.common.reflect.TypeToken;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayProxyRequest extends ProxyRequest<byte[]>{
    protected ByteArrayProxyRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public static ByteArrayProxyRequest of(HttpServerExchange exchange) {
        return new ByteArrayProxyRequest(exchange);
    }

    private static final Type _TYPE = new TypeToken<ByteArrayProxyRequest>(ByteArrayProxyRequest.class) {
        private static final long serialVersionUID = 7455691404944194247L;
    }.getType();

    public static Type type() {
        return _TYPE;
    }

    /**
     * @return the content as Json
     * @throws java.io.IOException
     */
    @Override
    public byte[] readContent() throws IOException {
        return BuffersUtils.toByteArray(getBuffer());
    }

    /**
     * updates the request content
     *
     * allocates the PooledByteBuffer array so close() must be invoked
     * to avoid memory leaks
     *
     * @param content
     * @throws java.io.IOException
     */
    @Override
    public void writeContent(byte[] content) throws IOException {
        if (content == null) {
            setBuffer(null);
        } else {
            PooledByteBuffer[] dest;
            if (isContentAvailable()) {
                dest = getBuffer();
            } else {
                dest = new PooledByteBuffer[MAX_BUFFERS];
                setBuffer(dest);
            }

            int copied = BuffersUtils.transfer(ByteBuffer.wrap(content), dest, wrapped);

            // updated request content length
            // this is not needed in Response.writeContent() since done
            // by ModificableContentSinkConduit.updateContentLenght();
            getHeaders().put(Headers.CONTENT_LENGTH, copied);
        }
    }
}
