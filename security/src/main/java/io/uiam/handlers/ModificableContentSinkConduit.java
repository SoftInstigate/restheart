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
package io.uiam.handlers;

import io.uiam.handlers.exchange.AbstractExchange;
import io.uiam.handlers.exchange.Response;
import static io.uiam.handlers.exchange.AbstractExchange.MAX_BUFFERS;
import io.uiam.handlers.exchange.ByteArrayResponse;
import io.uiam.plugins.PluginsRegistry;
import io.uiam.utils.BuffersUtils;
import io.uiam.utils.HttpStatus;
import io.undertow.connector.PooledByteBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.StreamSinkConduit;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.ServerFixedLengthStreamSinkConduit;
import io.undertow.util.Headers;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.Conduits;

/**
 * a conduit that buffers data allowing to modify it it also responsible of
 * executing response interceptors when terminateWrites() is called
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ModificableContentSinkConduit
        extends AbstractStreamSinkConduit<StreamSinkConduit> {

    static final Logger LOGGER = LoggerFactory.getLogger(
            ModificableContentSinkConduit.class);

    //private ByteBuffer data = null;
    private HttpServerExchange exchange;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param exchange
     */
    public ModificableContentSinkConduit(StreamSinkConduit next,
            HttpServerExchange exchange) {
        super(next);
        this.exchange = exchange;

        resetBufferPool(exchange);
    }

    /**
     * init buffers pool with a single, empty buffer.
     *
     * @param exchange
     * @return
     */
    private void resetBufferPool(HttpServerExchange exchange) {
        var buffers = new PooledByteBuffer[MAX_BUFFERS];
        exchange.putAttachment(Response.BUFFERED_RESPONSE_DATA,
                buffers);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return BuffersUtils.append(src,
                exchange.getAttachment(Response.BUFFERED_RESPONSE_DATA),
                exchange);
    }

    @Override
    public long write(ByteBuffer[] dsts, int offs, int len) throws IOException {
        for (int i = offs; i < len; ++i) {
            if (dsts[i].hasRemaining()) {
                return write(dsts[i]);
            }
        }
        return 0;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public void terminateWrites() throws IOException {
        var resp = ByteArrayResponse.wrap(exchange);

        if (!AbstractExchange.isInError(exchange)
                && !AbstractExchange.responseInterceptorsExecuted(exchange)) {
            AbstractExchange.setResponseInterceptorsExecuted(exchange);
            PluginsRegistry.getInstance()
                    .getResponseInterceptors()
                    .stream()
                    .filter(ri -> ri.resolve(exchange))
                    .forEachOrdered(ri -> {
                        LOGGER.debug("Executing response interceptor {} for {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath());

                        try {
                            ri.handleRequest(exchange);
                        }
                        catch (Exception ex) {
                            LOGGER.error("Error executing response interceptor {} for {}",
                                    ri.getClass().getSimpleName(),
                                    exchange.getRequestPath(),
                                    ex);
                            AbstractExchange.setInError(exchange);
                            // set error message
                            ByteArrayResponse response = ByteArrayResponse
                                    .wrap(exchange);

                            // dump bufferd content
                            BuffersUtils.dump("content buffer "
                                    + exchange.getRequestPath(),
                                    resp.getRawContent());

                            response.endExchangeWithMessage(
                                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                    "Error executing response interceptor "
                                    + ri.getClass().getSimpleName(),
                                    ex);
                        }
                    });
        }

        PooledByteBuffer[] dests = resp.getRawContent();

        updateContentLenght(exchange, dests);

        for (PooledByteBuffer dest : dests) {
            if (dest != null) {
                next.write(dest.getBuffer());
            }
        }

        next.terminateWrites();
    }

    private void updateContentLenght(HttpServerExchange exchange, PooledByteBuffer[] dests) {
        long length = 0;

        for (PooledByteBuffer dest : dests) {
            if (dest != null) {
                length += dest.getBuffer().limit();
            }
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, length);

        // need also to update lenght of ServerFixedLengthStreamSinkConduit
        if (next instanceof ServerFixedLengthStreamSinkConduit) {
            Method m;

            try {
                m = ServerFixedLengthStreamSinkConduit.class.getDeclaredMethod(
                        "reset",
                        long.class,
                        HttpServerExchange.class);
                m.setAccessible(true);
            }
            catch (NoSuchMethodException | SecurityException ex) {
                LOGGER.error("could not find ServerFixedLengthStreamSinkConduit.reset method", ex);
                throw new RuntimeException("could not find ServerFixedLengthStreamSinkConduit.reset method", ex);
            }

            try {
                m.invoke(next, length, exchange);
            }
            catch (Throwable ex) {
                LOGGER.error("could not access BUFFERED_REQUEST_DATA field", ex);
                throw new RuntimeException("could not access BUFFERED_REQUEST_DATA field", ex);
            }
        } else {
            LOGGER.warn("updateContentLenght() next is {}", next.getClass().getSimpleName());
        }
    }
}
