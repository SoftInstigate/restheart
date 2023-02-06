/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.restheart.exchange.ByteArrayProxyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

/**
 * conduit that executes response interceptors that don't require response
 * content; response interceptors that require response content are executed by
 * ModifiableContentSinkConduit.terminateWrites()
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ContentStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    static final Logger LOGGER = LoggerFactory.getLogger(ContentStreamSinkConduit.class);

    private final StreamSinkConduit _next;

    private final ResponseInterceptorsExecutor responseInterceptorsExecutor = new ResponseInterceptorsExecutor(true);

    /**
     * Construct a new instance.
     *
     * @param next
     * @param exchange
     */
    public ContentStreamSinkConduit(StreamSinkConduit next, HttpServerExchange exchange) {
        super(next);
        this._next = next;

        try {
            this.responseInterceptorsExecutor.handleRequest(exchange);
        } catch (Exception e) {
            LOGGER.error("Error executing interceptors", e);
            ByteArrayProxyRequest.of(exchange).setInError(true);
            throw new RuntimeException(e);
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return _next.write(src);
    }

    @Override
    public long write(ByteBuffer[] dsts, int offs, int len) throws IOException {
        return _next.write(dsts, offs, len);
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return _next.transferFrom(src, position, count);
    }

    @Override
    public long transferFrom(final StreamSourceChannel src, final long count, final ByteBuffer throughBuffer) throws IOException {
        return _next.transferFrom(src, count, throughBuffer);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return _next.writeFinal(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return _next.writeFinal(srcs, offset, length);
    }

    @Override
    public void terminateWrites() throws IOException {
        _next.terminateWrites();
    }
}
