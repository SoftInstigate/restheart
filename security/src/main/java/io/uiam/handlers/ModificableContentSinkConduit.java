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

import static io.uiam.handlers.AbstractExchange.MAX_BUFFERS;
import io.uiam.utils.BuffersUtils;
import io.undertow.connector.PooledByteBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.StreamSinkConduit;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Buffers;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.Conduits;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ModificableContentSinkConduit
        extends AbstractStreamSinkConduit<StreamSinkConduit> {

    static final Logger LOGGER = LoggerFactory.getLogger(
            ModificableContentSinkConduit.class);

    //private ByteBuffer data = null;
    private HttpServerExchange exchange;

    public static final AttachmentKey<PooledByteBuffer[]> BUFFERED_RESPONSE_DATA
            = AttachmentKey.create(PooledByteBuffer[].class);

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
        this.exchange.putAttachment(BUFFERED_RESPONSE_DATA,
                new PooledByteBuffer[MAX_BUFFERS]);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return BuffersUtils.transfer(src, 
                exchange.getAttachment(BUFFERED_RESPONSE_DATA),
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
        PooledByteBuffer[] dests = exchange.getAttachment(BUFFERED_RESPONSE_DATA);

        for (PooledByteBuffer dest : dests) {
            if (dest != null) {
                ByteBuffer src = dest.getBuffer();
                StringBuilder sb = new StringBuilder();

                Buffers.dump(src, sb, 1, 1);
                LOGGER.trace("{}", sb);

                super.write(src);
                //Connectors.updateResponseBytesSent(exchange, 0 - src.position());
            }
        }
    }

    public void flushToClient() throws IOException {
        super.terminateWrites();
    }
}
