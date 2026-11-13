/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.restheart.exchange.BufferedContent;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ImmediatePooledByteBuffer;

/**
 * Reads the request body without blocking the IO thread and attaches it to the
 * exchange as {@link BufferedContent}, then runs the rest of the pipeline.
 *
 * <p>When the body has not fully arrived, a read listener appends data as it
 * arrives: no thread is held while a slow client sends it, and the pipeline
 * resumes on the IO thread when the body is complete.</p>
 *
 * <p>The body is also put back into the request, so the code that reads the
 * request channel or input stream directly still gets it.</p>
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li><b>non-blocking services</b> ({@link #forNonBlockingService(int)}): they run
 *       on the IO thread and cannot block to read the body, so bodies up to the limit
 *       are read here (#799);</li>
 *   <li><b>blocking services</b> ({@link #forBlockingService(int)}): placed before
 *       {@link WorkingThreadsPoolDispatcher}, it reads bodies with a Content-Length
 *       up to the limit, so the virtual thread finds them in memory and never waits
 *       for the client; larger or chunked bodies are left to be streamed (#800).</li>
 * </ul>
 *
 * <p>A body larger than the limit is left in the request, as before 9.15.0: when its
 * Content-Length is known nothing is read, and when a chunked body exceeds the limit
 * while it is read, the bytes read so far are put back. The service then reads it as
 * it did before, e.g. with {@code exchange.getRequestReceiver()} (#803).</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.15.0
 */
public class RequestContentBuffer extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestContentBuffer.class);
    private static final int CHUNK_SIZE = 16 * 1024;

    private final int limit;
    private final boolean streamAboveLimit;

    private RequestContentBuffer(int limit, boolean streamAboveLimit) {
        super();
        this.limit = limit;
        this.streamAboveLimit = streamAboveLimit;
    }

    /**
     * @param limit bodies up to this size are read in advance; larger ones are left to the service
     * @return the handler for the pipeline of a non-blocking service
     */
    public static RequestContentBuffer forNonBlockingService(int limit) {
        return new RequestContentBuffer(limit, false);
    }

    /**
     * @param limit bodies with a Content-Length up to this size are read in advance; 0 disables it
     * @return the handler for the pipeline of a blocking service, or null if the limit is 0
     */
    public static RequestContentBuffer forBlockingService(int limit) {
        return limit > 0 ? new RequestContentBuffer(limit, true) : null;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (!BufferedContent.hasBody(exchange) || BufferedContent.get(exchange) != null) {
            next(exchange);
            return;
        }

        final var declared = exchange.getRequestContentLength();

        if (declared > limit || (streamAboveLimit && declared < 0)) {
            // left to the service: large bodies, and chunked bodies of blocking services,
            // which the virtual thread streams
            next(exchange);
            return;
        }

        final var channel = exchange.getRequestChannel();

        if (channel == null) {
            // the request channel was already taken
            next(exchange);
            return;
        }

        final var reader = new Reader(exchange, channel);

        switch (reader.read()) {
            case COMPLETE -> {
                reader.attach();
                next(exchange);
            }
            case OVERFLOW -> {
                reader.putBack();
                next(exchange);
            }
            case PENDING -> {
                // resumed when the call returns; the listener runs on the IO thread
                channel.getReadSetter().set(ch -> reader.onReadable());
                channel.resumeReads();
            }
            case FAILED -> {
                // the response was already set by the reader
            }
        }
    }

    private enum State {COMPLETE, OVERFLOW, PENDING, FAILED}

    private final class Reader {
        private final HttpServerExchange exchange;
        private final StreamSourceChannel channel;
        private final ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);
        private byte[] content;
        private int size = 0;

        Reader(HttpServerExchange exchange, StreamSourceChannel channel) {
            this.exchange = exchange;
            this.channel = channel;
            final var declared = exchange.getRequestContentLength();
            this.content = new byte[declared > 0 ? (int) declared : CHUNK_SIZE];
        }

        /**
         * Reads what is available without blocking.
         */
        State read() {
            try {
                while (true) {
                    buffer.clear();
                    final int r = channel.read(buffer);

                    if (r == -1) {
                        return State.COMPLETE;
                    } else if (r == 0) {
                        return State.PENDING;
                    }

                    if (size + r > limit) {
                        // the chunk just read stays in the buffer, putBack() returns it too
                        buffer.flip();
                        return State.OVERFLOW;
                    }

                    if (size + r > content.length) {
                        content = Arrays.copyOf(content, Math.min(limit, Math.max(content.length * 2, size + r)));
                    }

                    buffer.flip();
                    buffer.get(content, size, r);
                    size += r;
                }
            } catch (IOException ioe) {
                LOGGER.debug("error reading the request body", ioe);
                exchange.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                exchange.endExchange();
                return State.FAILED;
            }
        }

        void onReadable() {
            final var state = read();

            if (state == State.PENDING) {
                return;
            }

            channel.getReadSetter().set(null);
            channel.suspendReads();

            if (state == State.COMPLETE) {
                attach();
                Connectors.executeRootHandler(getNext(), exchange);
            } else if (state == State.OVERFLOW) {
                putBack();
                Connectors.executeRootHandler(getNext(), exchange);
            }
        }

        /**
         * Attaches the body as {@link BufferedContent}, read by {@code parseContent()} and
         * {@code ChannelReader}, and puts it back into the request, so that the code that reads
         * the request channel or input stream directly (e.g. Undertow's form parsers) gets it too.
         */
        void attach() {
            final var body = size == content.length ? content : Arrays.copyOf(content, size);
            BufferedContent.set(exchange, body);

            if (body.length > 0) {
                Connectors.ungetRequestBytes(exchange, new ImmediatePooledByteBuffer(ByteBuffer.wrap(body)));
            }

            Connectors.resetRequestChannel(exchange);
        }

        /**
         * Puts back into the request what was read of a body larger than the limit, without
         * attaching it, so that the service reads the whole body from the request.
         */
        void putBack() {
            final var overflow = new byte[buffer.remaining()];
            buffer.get(overflow);

            if (size > 0) {
                Connectors.ungetRequestBytes(exchange,
                        new ImmediatePooledByteBuffer(ByteBuffer.wrap(content, 0, size)),
                        new ImmediatePooledByteBuffer(ByteBuffer.wrap(overflow)));
            } else {
                Connectors.ungetRequestBytes(exchange, new ImmediatePooledByteBuffer(ByteBuffer.wrap(overflow)));
            }

            Connectors.resetRequestChannel(exchange);
        }
    }
}
