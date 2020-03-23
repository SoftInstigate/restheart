/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.handlers.exchange;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.IOException;
import java.nio.ByteBuffer;
import static org.restheart.handlers.exchange.AbstractExchange.MAX_BUFFERS;
import org.restheart.utils.BuffersUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ByteArrayRequest extends ProxableRequest<byte[]> {

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
            getWrappedExchange().getRequestHeaders().put(Headers.CONTENT_LENGTH, copied);
        }
    }
}
