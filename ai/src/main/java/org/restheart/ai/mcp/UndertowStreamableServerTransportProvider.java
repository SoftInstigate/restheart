/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import reactor.core.publisher.Mono;

/**
 * Undertow-native implementation of {@link McpStreamableServerTransportProvider}
 * for the MCP Streamable HTTP transport (2025-03-26 spec).
 *
 * <p>Ported from Sophia's {@code com.softinstigate.sophia.mcp.UndertowStreamableServerTransportProvider}
 * (already running in production there) — it has no dependency on anything Sophia-specific,
 * only on {@code org.restheart.exchange.ByteArrayRequest/Response} and the MCP SDK itself.
 *
 * <p>Supports all three HTTP methods on the MCP endpoint:
 * <ul>
 *   <li><b>POST</b> — JSON-RPC messages: {@code initialize} creates a session and
 *       returns JSON; tool-call requests return {@code text/event-stream}; notifications
 *       and responses return 202.</li>
 *   <li><b>GET</b> — opens a persistent SSE stream for server-to-client notifications
 *       on an existing session.</li>
 *   <li><b>DELETE</b> — closes and removes an existing session.</li>
 * </ul>
 *
 * <p>SSE is implemented with a {@link LinkedBlockingQueue} bridge: the worker thread
 * (blocking mode, default for RESTHeart services) blocks on the queue and flushes events
 * to the exchange output stream; the MCP SDK pushes events from its own thread via
 * {@link UndertowStreamableSessionTransport#sendMessage}.
 *
 * <p>This class has no dependency on Servlet or Spring; it integrates with RESTHeart
 * via {@link ByteArrayResponse#setCustomSender(Runnable)}.
 */
public class UndertowStreamableServerTransportProvider implements McpStreamableServerTransportProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(UndertowStreamableServerTransportProvider.class);

    static final String TEXT_EVENT_STREAM = "text/event-stream; charset=utf-8";
    static final String APPLICATION_JSON  = "application/json";
    static final String MESSAGE_EVENT_TYPE = "message";

    private final McpJsonMapper jsonMapper;
    private McpStreamableServerSession.Factory sessionFactory;
    private final ConcurrentHashMap<String, McpStreamableServerSession> sessions = new ConcurrentHashMap<>();
    private volatile boolean isClosing = false;

    public UndertowStreamableServerTransportProvider(McpJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    // -------------------------------------------------------------------------
    // McpStreamableServerTransportProvider contract
    // -------------------------------------------------------------------------

    @Override
    public List<String> protocolVersions() {
        return List.of(
            ProtocolVersions.MCP_2024_11_05,
            ProtocolVersions.MCP_2025_03_26,
            ProtocolVersions.MCP_2025_06_18
        );
    }

    @Override
    public void setSessionFactory(McpStreamableServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        if (sessions.isEmpty()) return Mono.empty();
        return Mono.fromRunnable(() ->
            sessions.values().parallelStream().forEach(session -> {
                try {
                    session.sendNotification(method, params).block();
                } catch (Exception e) {
                    LOGGER.error("Failed to notify session {}: {}", session.getId(), e.getMessage());
                }
            })
        );
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            isClosing = true;
            sessions.values().parallelStream().forEach(session -> {
                try { session.closeGracefully().block(); } catch (Exception ignored) {}
            });
            sessions.clear();
        });
    }

    // -------------------------------------------------------------------------
    // HTTP method handlers — called from McpService.handle()
    // -------------------------------------------------------------------------

    /**
     * Handles POST requests: initialize (creates a session), tool calls (SSE response),
     * notifications and responses (202 Accepted).
     */
    public void handlePost(ByteArrayRequest req, ByteArrayResponse res, McpTransportContext ctx) {
        if (isClosing) { res.setStatusCode(HttpStatus.SC_SERVICE_UNAVAILABLE); return; }

        byte[] body = req.getContent();
        if (body == null || body.length == 0) {
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            res.setContent("Empty request body");
            return;
        }

        McpSchema.JSONRPCMessage message;
        try {
            message = McpSchema.deserializeJsonRpcMessage(jsonMapper, new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            res.setContent("Invalid JSON-RPC: " + e.getMessage());
            return;
        }

        // --- initialize: create new session, return JSON with Mcp-Session-Id ---
        if (message instanceof McpSchema.JSONRPCRequest rpcReq
                && McpSchema.METHOD_INITIALIZE.equals(rpcReq.method())) {

            String accept = req.getHeader("Accept");
            if (accept == null || !accept.contains("text/event-stream") || !accept.contains("application/json")) {
                res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                res.setContent("Accept must include both text/event-stream and application/json");
                return;
            }

            try {
                var initReq = jsonMapper.convertValue(rpcReq.params(), new TypeRef<McpSchema.InitializeRequest>() {});
                var init = sessionFactory.startSession(initReq);
                sessions.put(init.session().getId(), init.session());

                McpSchema.InitializeResult result = init.initResult()
                        .contextWrite(c -> c.put(McpTransportContext.KEY, ctx))
                        .block();

                String json = jsonMapper.writeValueAsString(
                        new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, rpcReq.id(), result, null));

                res.getExchange().getResponseHeaders()
                        .put(new HttpString(HttpHeaders.MCP_SESSION_ID), init.session().getId());
                res.setContentType(APPLICATION_JSON);
                res.setContent(json.getBytes(StandardCharsets.UTF_8));
                res.setStatusCode(HttpStatus.SC_OK);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize session: {}", e.getMessage(), e);
                res.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                res.setContent("Failed to initialize session: " + e.getMessage());
            }
            return;
        }

        // --- all other messages require an existing session ---
        String sessionId = req.getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            res.setContent("mcp-session-id header required");
            return;
        }

        McpStreamableServerSession session = sessions.get(sessionId);
        if (session == null) {
            // Server is stateless — auto-recover by creating a new session transparently.
            // The stale session ID is typically caused by a server restart; since no application
            // state is lost (all state lives in the LLM context window), we can recreate the
            // transport session on the fly and continue processing the request normally.
            LOGGER.warn("Stale MCP session {}, auto-recovering new session", sessionId);
            try {
                var fakeInitReq = new McpSchema.InitializeRequest(
                        ProtocolVersions.MCP_2025_03_26,
                        new McpSchema.ClientCapabilities(null, null, null, null),
                        new McpSchema.Implementation("auto-recovered", "0.0.0"));
                var init = sessionFactory.startSession(fakeInitReq);
                init.initResult().contextWrite(c -> c.put(McpTransportContext.KEY, ctx)).block();
                session = init.session();
                sessions.put(session.getId(), session);
                res.getExchange().getResponseHeaders()
                        .put(new HttpString(HttpHeaders.MCP_SESSION_ID), session.getId());
                LOGGER.info("Auto-recovered MCP session: {} → {}", sessionId, session.getId());
            } catch (Exception e) {
                LOGGER.error("Session auto-recovery failed", e);
                res.setStatusCode(HttpStatus.SC_NOT_FOUND);
                res.setContent("Session expired and auto-recovery failed. Please reinitialize.");
                return;
            }
        }

        if (message instanceof McpSchema.JSONRPCResponse rpcResp) {
            session.accept(rpcResp).contextWrite(c -> c.put(McpTransportContext.KEY, ctx)).block();
            res.setStatusCode(HttpStatus.SC_ACCEPTED);

        } else if (message instanceof McpSchema.JSONRPCNotification rpcNotif) {
            session.accept(rpcNotif).contextWrite(c -> c.put(McpTransportContext.KEY, ctx)).block();
            res.setStatusCode(HttpStatus.SC_ACCEPTED);

        } else if (message instanceof McpSchema.JSONRPCRequest rpcReq) {
            // Tool call → SSE streaming response
            final var activeSession = session;
            var transport = new UndertowStreamableSessionTransport(sessionId, jsonMapper);
            res.setCustomSender(() -> {
                var exchange = res.getExchange();
                setSseHeaders(exchange);
                exchange.startBlocking();

                // Run responseStream on a virtual thread; it calls transport.sendMessage() as results arrive
                var vt = Thread.ofVirtual().start(() -> {
                    try {
                        activeSession.responseStream(rpcReq, transport)
                                .contextWrite(c -> c.put(McpTransportContext.KEY, ctx))
                                .block();
                    } catch (Exception e) {
                        LOGGER.warn("responseStream error for session {}: {}", sessionId, e.getMessage());
                    } finally {
                        transport.close();
                    }
                });

                drainQueueToExchange(transport, exchange);
                try { vt.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        } else {
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            res.setContent("Unknown JSON-RPC message type");
        }
    }

    /**
     * Handles GET requests: opens a persistent SSE stream for server-to-client
     * notifications on an existing session.
     */
    public void handleGet(ByteArrayRequest req, ByteArrayResponse res, McpTransportContext ctx) {
        if (isClosing) { res.setStatusCode(HttpStatus.SC_SERVICE_UNAVAILABLE); return; }

        String accept = req.getHeader("Accept");
        if (accept == null || !accept.contains("text/event-stream")) {
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            res.setContent("Accept: text/event-stream required");
            return;
        }

        String sessionId = req.getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            res.setContent("mcp-session-id header required");
            return;
        }

        McpStreamableServerSession session = sessions.get(sessionId);
        if (session == null) {
            // Stale session on GET (SSE listener): return 404 so the client re-opens the SSE
            // channel after the next successful POST (which will auto-recover the session).
            LOGGER.warn("Stale MCP session (GET/SSE): {}", sessionId);
            res.setStatusCode(HttpStatus.SC_NOT_FOUND);
            res.setContent("Session expired. Reconnect after sending a new request.");
            return;
        }

        var transport = new UndertowStreamableSessionTransport(sessionId, jsonMapper);
        var listeningStream = session.listeningStream(transport);

        res.setCustomSender(() -> {
            var exchange = res.getExchange();
            setSseHeaders(exchange);
            exchange.startBlocking();
            try {
                drainQueueToExchange(transport, exchange);
            } finally {
                listeningStream.close();
            }
        });
    }

    /**
     * Handles DELETE requests: terminates and removes an existing session.
     */
    public void handleDelete(ByteArrayRequest req, ByteArrayResponse res, McpTransportContext ctx) {
        if (isClosing) { res.setStatusCode(HttpStatus.SC_SERVICE_UNAVAILABLE); return; }

        String sessionId = req.getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            res.setContent("mcp-session-id header required");
            return;
        }

        McpStreamableServerSession session = sessions.remove(sessionId);
        if (session == null) {
            res.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }

        try {
            session.delete().contextWrite(c -> c.put(McpTransportContext.KEY, ctx)).block();
            res.setStatusCode(HttpStatus.SC_OK);
        } catch (Exception e) {
            LOGGER.error("Failed to delete session {}: {}", sessionId, e.getMessage());
            res.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void setSseHeaders(HttpServerExchange exchange) {
        exchange.setStatusCode(200);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, TEXT_EVENT_STREAM);
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "no-cache");
        exchange.getResponseHeaders().put(Headers.CONNECTION, "keep-alive");
        exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
    }

    /**
     * Blocks on the transport's event queue, writing SSE events to the exchange
     * output stream until the transport signals close (null sentinel).
     */
    private static void drainQueueToExchange(UndertowStreamableSessionTransport transport,
                                             HttpServerExchange exchange) {
        try {
            OutputStream out = exchange.getOutputStream();
            while (true) {
                String event = transport.take(); // blocks; null = close signal
                if (event == null) break;
                out.write(event.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException e) {
            LOGGER.debug("SSE stream I/O closed for session {}: {}", transport.sessionId, e.getMessage());
            transport.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            transport.close();
        } finally {
            exchange.endExchange();
        }
    }

    // -------------------------------------------------------------------------
    // Inner class: per-SSE-connection transport
    // -------------------------------------------------------------------------

    /**
     * Bridges the MCP SDK's reactive {@link McpStreamableServerTransport} interface
     * with Undertow's blocking I/O model via a {@link LinkedBlockingQueue}.
     *
     * <p>The SDK calls {@link #sendMessage} from its own thread; the worker thread
     * running the SSE response drains the queue via {@link #take()}.
     */
    static class UndertowStreamableSessionTransport implements McpStreamableServerTransport {

        private static final Logger LOGGER = LoggerFactory.getLogger(UndertowStreamableSessionTransport.class);

        final String sessionId;
        private final McpJsonMapper jsonMapper;
        private final LinkedBlockingQueue<Optional<String>> queue = new LinkedBlockingQueue<>();
        private volatile boolean closed = false;
        private final ReentrantLock lock = new ReentrantLock();

        UndertowStreamableSessionTransport(String sessionId, McpJsonMapper jsonMapper) {
            this.sessionId = sessionId;
            this.jsonMapper = jsonMapper;
        }

        /** Blocks until the next SSE event string is available; returns {@code null} on close. */
        String take() throws InterruptedException {
            var opt = queue.take();
            return opt.isPresent() ? opt.get() : null;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return sendMessage(message, null);
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message, String messageId) {
            return Mono.fromRunnable(() -> {
                if (closed) return;
                lock.lock();
                try {
                    if (closed) return;
                    String json = jsonMapper.writeValueAsString(message);
                    queue.put(Optional.of(formatSseEvent(MESSAGE_EVENT_TYPE, json, messageId)));
                } catch (Exception e) {
                    LOGGER.error("Failed to queue SSE message for session {}: {}", sessionId, e.getMessage());
                } finally {
                    lock.unlock();
                }
            });
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return jsonMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(this::close);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                try {
                    queue.put(Optional.empty()); // sentinel
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private static String formatSseEvent(String eventType, String data, String id) {
            var sb = new StringBuilder();
            if (id != null) sb.append("id: ").append(id).append('\n');
            sb.append("event: ").append(eventType).append('\n');
            sb.append("data: ").append(data).append("\n\n");
            return sb.toString();
        }
    }
}
