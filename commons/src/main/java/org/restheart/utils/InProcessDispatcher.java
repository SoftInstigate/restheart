/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.conduits.ConduitStreamSourceChannel;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

/**
 * Dispatches an HTTP request to an Undertow handler chain without a socket.
 *
 * <p>
 * PROOF OF CONCEPT. The request goes through the real Undertow server side: an
 * {@link HttpOpenListener} is given one end of an XNIO full-duplex pipe, exactly as
 * it is given an accepted socket, and opens a genuine {@code HttpServerConnection}
 * on it, with its own parser and a real {@code HttpServerExchange}. The other end of
 * the pipe is this class, acting as a minimal HTTP/1.1 client.
 * </p>
 *
 * <p>
 * Nothing is faked: whatever the handler chain does on a socket connection, it does
 * here. Compared to a TCP loopback there is no port, no handshake and no TCP stack,
 * although the bytes still cross the kernel, because XNIO builds its pipes on
 * {@link java.nio.channels.Pipe}.
 * </p>
 *
 * <p>
 * Pipes are persistent. A {@link Lane} is one pipe with one keep-alive server
 * connection on it; a dispatch takes an idle lane from the pool, or opens one if
 * none is idle, sends one request, waits for its whole response and gives the lane
 * back. Requests on a lane are strictly sequential, so the pool holds about as many
 * lanes as the peak number of concurrent dispatches. A lane the server closes, or
 * that fails in any way, is dropped.
 * </p>
 *
 * <p>
 * The request bytes are written from the calling thread, which for a small request
 * on an idle pipe always succeeds at once; a write listener takes over only if the
 * pipe buffer fills. The response is read by a listener on the XNIO I/O thread that
 * stays armed for the life of the lane, and is framed incrementally by
 * {@code Content-Length} or chunked transfer coding. The calling thread waits on a
 * future, a virtual-thread-friendly wait: it never blocks on a channel, which with
 * XNIO would mean a temporary selector per thread.
 * </p>
 *
 * <p>
 * The pipe has no peer address, so the exchange is given the loopback address as
 * source and destination before the chain runs: handlers that log or check the
 * client address find one. The exchange also carries the {@link #IN_PROCESS} marker,
 * so that handlers whose work only makes sense for an external client can step aside.
 * </p>
 */
public class InProcessDispatcher {
    /** what came back: status code, response headers and the whole body */
    public record Response(int status, HeaderMap headers, byte[] body) {
        public String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /**
     * Marks an exchange that entered the handler chain through this dispatcher rather
     * than through a listener. Handlers whose work only makes sense for an external
     * client (request logging, CORS, metering) can step aside for such an exchange;
     * authentication, authorization, interceptors and services must not look at it.
     * Read it with {@code Exchange.isInProcess(exchange)}.
     */
    public static final AttachmentKey<Boolean> IN_PROCESS = AttachmentKey.create(Boolean.class);

    private static final InetSocketAddress LOOPBACK = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    private static final int READ_BUFFER_SIZE = 16 * 1024;

    private final Supplier<XnioWorker> worker;
    private final Supplier<ByteBufferPool> bufferPool;
    private final Supplier<HttpHandler> rootHandler;
    private final Supplier<OptionMap> undertowOptions;
    private final Duration timeout;

    private volatile HttpOpenListener openListener;

    private final ConcurrentLinkedQueue<Lane> idleLanes = new ConcurrentLinkedQueue<>();
    private final AtomicInteger openLanes = new AtomicInteger();

    /**
     * Lazily resolved variant, for a dispatcher created before the server it dispatches to
     * has been built (the suppliers are consulted on the first dispatch).
     */
    public InProcessDispatcher(Supplier<XnioWorker> worker, Supplier<ByteBufferPool> bufferPool, Supplier<OptionMap> undertowOptions, Supplier<HttpHandler> rootHandler, Duration timeout) {
        this.worker = worker;
        this.bufferPool = bufferPool;
        this.undertowOptions = undertowOptions;
        this.rootHandler = rootHandler;
        this.timeout = timeout;
    }

    public InProcessDispatcher(XnioWorker worker, ByteBufferPool bufferPool, OptionMap undertowOptions, HttpHandler rootHandler, Duration timeout) {
        this(() -> worker, () -> bufferPool, () -> undertowOptions, () -> rootHandler, timeout);
    }

    /**
     * Dispatches one request and waits for the whole response.
     *
     * @param method  the HTTP method
     * @param target  the request target, path plus optional query string, as it would appear on the request line
     * @param headers the request headers; {@code Host} defaults to {@code localhost}; framing and connection headers are replaced
     * @param body    the request body, or {@code null}
     * @return the response
     * @throws IOException      if the pipe cannot be created or the exchange fails
     * @throws TimeoutException if the response does not complete within the timeout; the lane is dropped
     */
    public Response dispatch(HttpString method, String target, HeaderMap headers, byte[] body) throws IOException, TimeoutException {
        var lane = idleLanes.poll();

        if (lane == null) {
            lane = new Lane();
        }

        var pending = new Pending(method);
        var keepLane = false;

        try {
            lane.pending = pending;
            lane.send(encode(method, target, headers, body));

            var response = pending.future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            keepLane = pending.parser.keepAlive();

            return response;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for the in-process response", ie);
        } catch (ExecutionException ee) {
            throw ee.getCause() instanceof IOException ioe ? ioe : new IOException(ee.getCause());
        } finally {
            lane.pending = null;

            if (keepLane && !lane.closed) {
                idleLanes.add(lane);
            } else {
                lane.close();
            }
        }
    }

    /** @return how many lanes, idle or busy, are open right now */
    public int openLanes() {
        return openLanes.get();
    }

    /** @return how many lanes are idle right now */
    public int idleLanes() {
        return idleLanes.size();
    }

    /** closes every idle lane; busy lanes close when their dispatch returns */
    public void close() {
        Lane lane;

        while ((lane = idleLanes.poll()) != null) {
            lane.close();
        }
    }

    private HttpOpenListener openListener() {
        var ol = openListener;

        if (ol == null) {
            synchronized (this) {
                ol = openListener;
                if (ol == null) {
                    var handler = rootHandler.get();
                    ol = new HttpOpenListener(bufferPool.get(), undertowOptions.get());
                    ol.setRootHandler(exchange -> {
                        // a pipe has no peer: give the exchange an address before anything reads it
                        exchange.setSourceAddress(LOOPBACK);
                        exchange.setDestinationAddress(LOOPBACK);
                        exchange.putAttachment(IN_PROCESS, Boolean.TRUE);
                        handler.handleRequest(exchange);
                    });
                    openListener = ol;
                }
            }
        }

        return ol;
    }

    // -------------------------------------------------------------------------
    // a lane: one persistent pipe, one keep-alive server connection, one request at a time
    // -------------------------------------------------------------------------

    /** the request in flight on a lane, if any */
    private static final class Pending {
        final ResponseParser parser;
        final CompletableFuture<Response> future = new CompletableFuture<>();

        Pending(HttpString method) {
            this.parser = new ResponseParser(method.equals(Methods.HEAD));
        }
    }

    private final class Lane {
        private final StreamConnection serverSide;
        private final StreamConnection clientSide;

        // one buffer per lane: XNIO binds a connection to one I/O thread, so onReadable never runs concurrently with itself
        private final ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);

        volatile Pending pending;
        volatile boolean closed;

        Lane() throws IOException {
            var pipe = worker.get().createFullDuplexPipeConnection();
            this.serverSide = pipe.getLeftSide();
            this.clientSide = pipe.getRightSide();

            openLanes.incrementAndGet();

            // the server accepts its end of the pipe exactly as it accepts a socket
            openListener().handleEvent(serverSide);

            // the reader stays armed for the life of the lane: it frames responses and notices a server close
            clientSide.getSourceChannel().setReadListener(this::onReadable);
            clientSide.getSourceChannel().resumeReads();
        }

        void send(byte[] request) throws IOException {
            var buffer = ByteBuffer.wrap(request);
            var sink = clientSide.getSinkChannel();

            // write from the calling thread: an idle pipe takes a small request at once
            while (buffer.hasRemaining()) {
                if (sink.write(buffer) == 0) {
                    break;
                }
            }

            if (!buffer.hasRemaining()) {
                sink.flush();
                return;
            }

            // the pipe buffer is full: the listener writes the rest as room frees up
            var p = pending;

            sink.setWriteListener(channel -> {
                try {
                    while (buffer.hasRemaining()) {
                        if (channel.write(buffer) == 0) {
                            return;
                        }
                    }

                    channel.suspendWrites();
                    channel.flush();
                } catch (IOException ioe) {
                    fail(p, ioe);
                }
            });

            sink.resumeWrites();
        }

        private void onReadable(ConduitStreamSourceChannel channel) {
            var p = pending;
            var buffer = readBuffer;
            buffer.clear();

            try {
                int n;

                while ((n = channel.read(buffer)) > 0) {
                    buffer.flip();

                    if (p == null) {
                        // bytes with no request in flight: the lane is out of sync, drop it
                        throw new IOException("unexpected " + buffer.remaining() + " bytes on an idle in-process lane");
                    }

                    p.parser.feed(buffer);
                    buffer.clear();

                    if (p.parser.complete()) {
                        p.future.complete(p.parser.response());
                        return;
                    }
                }

                if (n == -1) {
                    // the server closed its end: after a close-delimited response that is the end of the body
                    close();

                    if (p != null) {
                        if (p.parser.completeOnClose()) {
                            p.future.complete(p.parser.response());
                        } else {
                            p.future.completeExceptionally(new IOException("the in-process connection closed before the response completed"));
                        }
                    }
                }
            } catch (IOException ioe) {
                fail(p, ioe);
            }
        }

        private void fail(Pending p, IOException ioe) {
            close();

            if (p != null) {
                p.future.completeExceptionally(ioe);
            }
        }

        void close() {
            if (!closed) {
                closed = true;
                openLanes.decrementAndGet();
                idleLanes.remove(this);
                IoUtils.safeClose(clientSide);
                IoUtils.safeClose(serverSide);
            }
        }
    }

    // -------------------------------------------------------------------------
    // HTTP/1.1 encoding
    // -------------------------------------------------------------------------

    private static byte[] encode(HttpString method, String target, HeaderMap headers, byte[] body) {
        var content = body == null ? new byte[0] : body;
        var host = headers != null && headers.contains(Headers.HOST) ? headers.getFirst(Headers.HOST) : "localhost";

        var sb = new StringBuilder(256)
                .append(method).append(' ').append(target).append(" HTTP/1.1\r\n")
                .append("Host: ").append(host).append("\r\n")
                .append("Content-Length: ").append(content.length).append("\r\n");

        if (headers != null) {
            for (var values : headers) {
                var name = values.getHeaderName();

                if (name.equals(Headers.HOST) || name.equals(Headers.CONTENT_LENGTH) || name.equals(Headers.TRANSFER_ENCODING) || name.equals(Headers.CONNECTION) || name.equals(Headers.EXPECT)) {
                    continue;
                }

                for (var value : values) {
                    sb.append(name).append(": ").append(value).append("\r\n");
                }
            }
        }

        sb.append("\r\n");

        var head = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        var request = Arrays.copyOf(head, head.length + content.length);
        System.arraycopy(content, 0, request, head.length, content.length);

        return request;
    }

    // -------------------------------------------------------------------------
    // HTTP/1.1 incremental response parsing
    // -------------------------------------------------------------------------

    /**
     * Frames one HTTP/1.1 response as its bytes arrive. Head first, then the body by
     * {@code Content-Length}, by chunked transfer coding, or until the connection closes
     * when the response says {@code Connection: close} without framing.
     */
    static final class ResponseParser {
        private enum State { HEAD, BODY_LENGTH, CHUNK_SIZE, CHUNK_DATA, CHUNK_DATA_END, TRAILERS, BODY_UNTIL_CLOSE, DONE }

        private final boolean headRequest;
        private final ByteArrayOutputStream head = new ByteArrayOutputStream(512);
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final StringBuilder line = new StringBuilder();

        private State state = State.HEAD;
        private int status;
        private HeaderMap headers;
        private boolean connectionClose;
        private long remaining;

        ResponseParser(boolean headRequest) {
            this.headRequest = headRequest;
        }

        boolean complete() {
            return state == State.DONE;
        }

        /** whether a close from the server, now, ends a well-formed response */
        boolean completeOnClose() {
            return state == State.BODY_UNTIL_CLOSE;
        }

        /** whether the lane can carry another request after this response */
        boolean keepAlive() {
            return state == State.DONE && !connectionClose;
        }

        Response response() {
            return new Response(status, headers, body.toByteArray());
        }

        void feed(ByteBuffer in) throws IOException {
            while (in.hasRemaining()) {
                switch (state) {
                    case HEAD -> readHead(in);
                    case BODY_LENGTH -> {
                        var n = (int) Math.min(remaining, in.remaining());
                        body.write(in.array(), in.arrayOffset() + in.position(), n);
                        in.position(in.position() + n);
                        remaining -= n;
                        if (remaining == 0) {
                            state = State.DONE;
                        }
                    }
                    case CHUNK_SIZE -> {
                        if (readLine(in)) {
                            var text = line.toString();
                            var semicolon = text.indexOf(';');
                            remaining = Long.parseLong((semicolon < 0 ? text : text.substring(0, semicolon)).trim(), 16);
                            line.setLength(0);
                            state = remaining == 0 ? State.TRAILERS : State.CHUNK_DATA;
                        }
                    }
                    case CHUNK_DATA -> {
                        var n = (int) Math.min(remaining, in.remaining());
                        body.write(in.array(), in.arrayOffset() + in.position(), n);
                        in.position(in.position() + n);
                        remaining -= n;
                        if (remaining == 0) {
                            state = State.CHUNK_DATA_END;
                        }
                    }
                    case CHUNK_DATA_END -> {
                        if (readLine(in)) { // the CRLF after the chunk data
                            line.setLength(0);
                            state = State.CHUNK_SIZE;
                        }
                    }
                    case TRAILERS -> {
                        if (readLine(in)) {
                            var empty = line.isEmpty();
                            line.setLength(0);
                            if (empty) {
                                state = State.DONE;
                            }
                        }
                    }
                    case BODY_UNTIL_CLOSE -> {
                        body.write(in.array(), in.arrayOffset() + in.position(), in.remaining());
                        in.position(in.limit());
                    }
                    case DONE -> throw new IOException("bytes past the end of the in-process response");
                }

                if (state == State.DONE) {
                    if (in.hasRemaining()) {
                        throw new IOException("bytes past the end of the in-process response");
                    }
                    return;
                }
            }
        }

        private int tail; // the last four bytes of the head seen so far, newest in the low byte

        private void readHead(ByteBuffer in) throws IOException {
            while (in.hasRemaining()) {
                var b = in.get();
                head.write(b);
                tail = (tail << 8) | (b & 0xff);

                if (tail == 0x0d0a0d0a) { // CR LF CR LF
                    var bytes = head.toByteArray();
                    parseHead(bytes, bytes.length - 4);
                    return;
                }
            }
        }

        /** reads one CRLF-terminated line into {@code line}; true when the line is complete */
        private boolean readLine(ByteBuffer in) {
            while (in.hasRemaining()) {
                var c = (char) (in.get() & 0xff);

                if (c == '\n') {
                    if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                        line.setLength(line.length() - 1);
                    }
                    return true;
                }

                line.append(c);
            }

            return false;
        }

        private void parseHead(byte[] bytes, int length) throws IOException {
            var lines = new String(bytes, 0, length, StandardCharsets.ISO_8859_1).split("\r\n");

            if (lines.length == 0 || !lines[0].startsWith("HTTP/1.")) {
                throw new IOException("malformed in-process response: bad status line");
            }

            status = Integer.parseInt(lines[0].split(" ", 3)[1]);
            headers = new HeaderMap();

            var chunked = false;
            var contentLength = -1L;

            for (var i = 1; i < lines.length; i++) {
                var colon = lines[i].indexOf(':');

                if (colon < 0) {
                    continue;
                }

                var name = lines[i].substring(0, colon).trim();
                var value = lines[i].substring(colon + 1).trim();
                headers.add(HttpString.tryFromString(name), value);

                switch (name.toLowerCase(Locale.ROOT)) {
                    case "transfer-encoding" -> chunked = value.toLowerCase(Locale.ROOT).contains("chunked");
                    case "content-length" -> contentLength = Long.parseLong(value);
                    case "connection" -> connectionClose = value.toLowerCase(Locale.ROOT).contains("close");
                    default -> { }
                }
            }

            var bodiless = headRequest || status / 100 == 1 || status == 204 || status == 304;

            if (bodiless) {
                state = State.DONE;
            } else if (chunked) {
                state = State.CHUNK_SIZE;
            } else if (contentLength >= 0) {
                remaining = contentLength;
                state = remaining == 0 ? State.DONE : State.BODY_LENGTH;
            } else if (connectionClose) {
                state = State.BODY_UNTIL_CLOSE;
            } else {
                // a persistent response with no framing carries no body
                state = State.DONE;
            }
        }
    }
}
