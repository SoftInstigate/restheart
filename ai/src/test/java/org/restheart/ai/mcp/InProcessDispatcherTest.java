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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.InProcessDispatcher;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import com.google.gson.JsonParser;

import io.undertow.UndertowOptions;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

/**
 * PROOF OF CONCEPT: a request dispatched over an XNIO pipe reaches a real Undertow
 * handler chain, with a real exchange, and the response comes back whole.
 */
public class InProcessDispatcherTest {
    private static XnioWorker worker;
    private static DefaultByteBufferPool pool;

    /** echoes method, request target, one header and the body as JSON, with status 201 */
    private static final HttpHandler ECHO = exchange -> {
        if (exchange.isInIoThread()) {
            exchange.dispatch(InProcessDispatcherTest::echo);
            return;
        }
        echo(exchange);
    };

    private static void echo(io.undertow.server.HttpServerExchange exchange) throws Exception {
        exchange.startBlocking();
        var body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        var json = String.format("{\"method\":\"%s\",\"path\":\"%s\",\"query\":\"%s\",\"host\":\"%s\",\"x\":\"%s\",\"peer\":\"%s\",\"inProcess\":%s,\"body\":%s}",
                exchange.getRequestMethod(),
                exchange.getRequestPath(),
                exchange.getQueryString(),
                exchange.getHostAndPort(),
                exchange.getRequestHeaders().getFirst("X-Test"),
                exchange.getSourceAddress() == null ? "" : exchange.getSourceAddress().getAddress().getHostAddress(),
                Exchange.isInProcess(exchange),
                body.isEmpty() ? "null" : body);

        exchange.setStatusCode(201);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(json);
    }

    @BeforeAll
    static void start() throws Exception {
        worker = Xnio.getInstance().createWorker(OptionMap.create(Options.WORKER_IO_THREADS, 2));
        pool = new DefaultByteBufferPool(false, 8 * 1024);
    }

    @AfterAll
    static void stop() {
        worker.shutdown();
        pool.close();
    }

    private static InProcessDispatcher dispatcher(HttpHandler handler) {
        return new InProcessDispatcher(worker, pool, OptionMap.EMPTY, handler, Duration.ofSeconds(5));
    }

    @Test
    public void requestReachesTheChainAndTheResponseComesBack() throws Exception {
        var headers = new HeaderMap();
        headers.put(Headers.HOST, "c0ffee.example.com:8080");
        headers.put(HttpString.tryFromString("X-Test"), "yes");
        headers.put(Headers.CONTENT_TYPE, "application/json");

        var body = "{\"title\":\"buy milk\"}".getBytes(StandardCharsets.UTF_8);

        var response = dispatcher(ECHO).dispatch(Methods.POST, "/todos?page=2", headers, body);

        assertEquals(201, response.status());
        assertEquals("application/json", response.headers().getFirst(Headers.CONTENT_TYPE));

        var echoed = JsonParser.parseString(response.bodyAsString()).getAsJsonObject();
        assertEquals("POST", echoed.get("method").getAsString());
        assertEquals("/todos", echoed.get("path").getAsString());
        assertEquals("page=2", echoed.get("query").getAsString());
        assertEquals("c0ffee.example.com:8080", echoed.get("host").getAsString());
        assertEquals("yes", echoed.get("x").getAsString());
        assertTrue(echoed.get("inProcess").getAsBoolean(), "the in-process marker must reach the handler");
        assertEquals("buy milk", echoed.getAsJsonObject("body").get("title").getAsString());
    }

    @Test
    public void theExchangeHasASourceAddress() throws Exception {
        var response = dispatcher(ECHO).dispatch(Methods.GET, "/", new HeaderMap(), null);

        var echoed = JsonParser.parseString(response.bodyAsString()).getAsJsonObject();
        var peer = echoed.get("peer").getAsString();
        assertNotNull(peer);
        assertTrue(peer.startsWith("127.") || peer.contains(":"), "loopback address expected, got " + peer);
    }

    @Test
    public void aResponseWithoutContentLengthIsReadWhole() throws Exception {
        // the blocking output stream, closed without a declared length: Undertow frames it as chunked or close-delimited
        HttpHandler stream = exchange -> {
            exchange.startBlocking();
            exchange.setStatusCode(200);
            var out = exchange.getOutputStream();
            for (var i = 0;i < 1_000;i++) {
                out.write(("line " + i + "\n").getBytes(StandardCharsets.UTF_8));
            }
            out.close();
        };
        HttpHandler streaming = exchange -> {
            if (exchange.isInIoThread()) {
                exchange.dispatch(stream);
                return;
            }
            stream.handleRequest(exchange);
        };

        var response = dispatcher(streaming).dispatch(Methods.GET, "/stream", new HeaderMap(), null);

        assertEquals(200, response.status());
        var lines = response.bodyAsString().split("\n");
        assertEquals(1_000, lines.length);
        assertEquals("line 0", lines[0]);
        assertEquals("line 999", lines[999]);
    }

    @Test
    public void aStatusOtherThanSuccessComesBackAsIs() throws Exception {
        HttpHandler notFound = exchange -> {
            exchange.setStatusCode(404);
            exchange.getResponseSender().send("{\"message\":\"not found\"}");
        };

        var response = dispatcher(notFound).dispatch(Methods.DELETE, "/missing", new HeaderMap(), null);

        assertEquals(404, response.status());
        assertEquals("not found", JsonParser.parseString(response.bodyAsString()).getAsJsonObject().get("message").getAsString());
    }

    @Test
    public void sequentialDispatchesReuseOneLane() throws Exception {
        var dispatcher = dispatcher(ECHO);

        for (var i = 0;i < 50;i++) {
            var response = dispatcher.dispatch(Methods.GET, "/seq/" + i, new HeaderMap(), null);
            assertEquals(201, response.status());
            assertEquals("/seq/" + i, JsonParser.parseString(response.bodyAsString()).getAsJsonObject().get("path").getAsString());
        }

        assertEquals(1, dispatcher.openLanes(), "one persistent lane expected");
        assertEquals(1, dispatcher.idleLanes(), "the lane must be back in the pool");
    }

    @Test
    public void aBodilessResponseDoesNotStallTheLane() throws Exception {
        HttpHandler noContent = exchange -> exchange.setStatusCode(204);
        var dispatcher = dispatcher(noContent);

        var first = dispatcher.dispatch(Methods.DELETE, "/x", new HeaderMap(), null);
        var second = dispatcher.dispatch(Methods.DELETE, "/y", new HeaderMap(), null);

        assertEquals(204, first.status());
        assertEquals(204, second.status());
        assertEquals(0, second.body().length);
        assertEquals(1, dispatcher.openLanes());
    }

    @Test
    public void concurrentDispatchesDoNotMixUp() throws Exception {
        var dispatcher = dispatcher(ECHO);
        var tasks = new ArrayList<Callable<String>>();

        for (var i = 0;i < 200;i++) {
            var n = i;
            tasks.add(() -> {
                var body = ("{\"n\":" + n + "}").getBytes(StandardCharsets.UTF_8);
                var response = dispatcher.dispatch(Methods.POST, "/n/" + n, new HeaderMap(), body);
                var echoed = JsonParser.parseString(response.bodyAsString()).getAsJsonObject();
                return echoed.get("path").getAsString() + "=" + echoed.getAsJsonObject("body").get("n").getAsInt();
            });
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<Future<String>>();
            for (var task : tasks) {
                results.add(executor.submit(task));
            }
            for (var i = 0;i < results.size();i++) {
                assertEquals("/n/" + i + "=" + i, results.get(i).get());
            }
        }

        assertTrue(dispatcher.openLanes() <= 200, "no more lanes than dispatches, got " + dispatcher.openLanes());
        assertEquals(dispatcher.openLanes(), dispatcher.idleLanes(), "every lane must be back in the pool");
    }

    /** a JSON string of {@code n} characters, as the body the echo handler embeds */
    private static byte[] jsonStringOf(int n) {
        return ("\"" + "x".repeat(n) + "\"").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void aBodyOverMaxEntitySizeDoesNotReachTheHandlerWhole() throws Exception {
        var options = OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, 1024L);
        var dispatcher = new InProcessDispatcher(worker, pool, options, ECHO, Duration.ofSeconds(5));

        try {
            var response = dispatcher.dispatch(Methods.POST, "/big", new HeaderMap(), jsonStringOf(4096));
            assertNotEquals(201, response.status(), "a body over MAX_ENTITY_SIZE must be refused in-process as from the network");
        } catch (IOException terminated) {
            // Undertow may terminate the connection instead of answering: refused all the same
        }
    }

    @Test
    public void aBodyWithinMaxEntitySizePasses() throws Exception {
        var options = OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, 1024L);
        var dispatcher = new InProcessDispatcher(worker, pool, options, ECHO, Duration.ofSeconds(5));

        var response = dispatcher.dispatch(Methods.POST, "/small", new HeaderMap(), jsonStringOf(512));

        assertEquals(201, response.status());
    }

    @Test
    public void theTimeoutIsLongByDefaultAndMustBePositive() {
        assertEquals(Duration.ofMinutes(10), InProcessDispatcher.DEFAULT_TIMEOUT);

        var dispatcher = dispatcher(ECHO);
        dispatcher.setTimeout(Duration.ofSeconds(90));
        assertEquals(Duration.ofSeconds(90), dispatcher.timeout());

        assertThrows(IllegalArgumentException.class, () -> dispatcher.setTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.setTimeout(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.setTimeout(null));
    }
}
