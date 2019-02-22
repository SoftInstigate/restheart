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

import io.uiam.Bootstrapper;
import static io.uiam.handlers.ResponseProxyInterceptorsHandler.LOGGER;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.Connectors;
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
import org.xnio.conduits.WriteReadyHandler;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ModificableContentSinkConduit
        extends AbstractStreamSinkConduit<StreamSinkConduit> {

    static final Logger LOGGER = LoggerFactory.getLogger(
            ModificableContentSinkConduit.class);

    private ByteBuffer data = null;

    public static final AttachmentKey<PooledByteBuffer[]> BUFFERED_RESPONSE_DATA
            = AttachmentKey.create(PooledByteBuffer[].class);

    public static final int MAX_CONTENT_SIZE = 16 * 1024 * 1024; // 16byte

    public static final int MAX_BUFFERS;

    static {
        MAX_BUFFERS = 1 + (MAX_CONTENT_SIZE / (Bootstrapper.getConfiguration() != null
                ? Bootstrapper.getConfiguration().getBufferSize()
                : 1024));

        LOGGER.info("The maximum size for request content "
                + "is {} bytes.",
                MAX_CONTENT_SIZE,
                MAX_BUFFERS,
                Bootstrapper.getConfiguration() != null
                ? Bootstrapper.getConfiguration().getBufferSize()
                : 16384);
    }

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     * @param exchange
     */
    public ModificableContentSinkConduit(StreamSinkConduit next, HttpServerExchange exchange) {
        super(next);
        setWriteReadyHandler(new WriteReadyHandler.ChannelListenerHandler<>(
                Connectors.getConduitSinkChannel(exchange)));
        reset();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int copied = 0;

        if (src.hasRemaining()) {
            copied += Buffers.copy(data, src);
            src.flip();
        }

        return copied;
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
        LOGGER.debug("terminateWrites 2");

        reset();

        write(ByteBuffer.wrap("{\"ciao\":1}".getBytes()));

        LOGGER.debug("modified data {}", dump());

        //data.flip();
        super.write(data);
        super.terminateWrites();
    }

    public ByteBuffer getData() {
        return data;
    }

    public void reset() {
        this.data = ByteBuffer.allocate(16 * 1024);
    }

    public void writeFinalToClient() throws IOException {
        LOGGER.debug("sending data {}", dump());
        data.flip();
        super.write(data);
    }

    private StringBuilder dump() {
        data.flip();
        StringBuilder sb = new StringBuilder();
        try {
            Buffers.dump(data, sb, 0, 20);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return sb;
    }
}
