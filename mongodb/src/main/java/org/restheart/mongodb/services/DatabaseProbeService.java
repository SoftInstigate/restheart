/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

package org.restheart.mongodb.services;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;

/**
 * Lightweight DB connectivity probe.
 * 
 * This performs a minimal, non-destructive ping (db.runCommand({ ping: 1 }))
 * to verify connectivity. It's intended for readiness checks and MUST be
 * cheap. It does not perform any writes.
 *
 * GET /health/db -> { status: "ok", latencyMs: N, description: "ping successful" }
 * or { status: "error", latencyMs: N, description: "error description" }
 * HEAD /health/db -> no body
 * HTTP status codes: 200 (OK), 503 (unavailable), 504 (timeout), 429 (too many requests)
 * 
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "database-probe",
    description = "lightweight DB connectivity probe",
    secure = false,
    defaultURI = "/health/db",
    blocking = true)
public class DatabaseProbeService implements JsonService {

    // JSON response fields
    private static final String STATUS = "status";
    private static final String DESCRIPTION = "description";
    private static final String LATENCY_IN_MILLIS = "latencyMs";

    // Fixed concurrency cap for probes
    private static final int MAX_CONCURRENCY = 5;
    private static final int QUEUE_SIZE = 10;
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("config")
    private Map<String, Object> config;

    private final ThreadPoolExecutor pingExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENCY,
            MAX_CONCURRENCY,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_SIZE),
            r -> {
                final Thread t = new Thread(r, "db-probe-exec-" + THREAD_COUNTER.incrementAndGet());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    // configurable timeout (ms)
    private long timeoutMs = 2000L;
    // configurable database name to ping
    private String dbName = "admin";

    @OnInit
    public void init() {
        // read timeout from config if present
        this.timeoutMs = ((Number) argOrDefault(this.config, "timeout-ms", Long.valueOf(this.timeoutMs))).longValue();
        // read db name from config if present
        this.dbName = (String) argOrDefault(this.config, "dbname", this.dbName);
    }

    @Override
    public void handle(final JsonRequest request, final JsonResponse response) {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        }

        if (!request.isGet() && !request.isHead()) {
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        sendPingAndAwait(request, response, this.timeoutMs);
    }

    private void sendPingAndAwait(final JsonRequest request, final JsonResponse response, long timeoutMs) {
        final boolean wantsBody = request.isGet();
        final long start = System.nanoTime();

        final CompletableFuture<BsonDocument> future;
        try {
            final var pingCmd = new BsonDocument("ping", new BsonInt32(1));
            future = CompletableFuture.supplyAsync(
                    () -> mclient.getDatabase(this.dbName).runCommand(pingCmd, BsonDocument.class),
                    this.pingExecutor);
        } catch (final RejectedExecutionException ree) {
            if (wantsBody) {
                setResponseContent(response, ProbeStatus.ERROR, "too many concurrent probes",
                        pingDuration(start));
            }
            response.setStatusCode(HttpStatus.SC_TOO_MANY_REQUESTS); // Too Many Requests
            return;
        }

        try {
            // wait with timeout
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (wantsBody) {
                setResponseContent(response, ProbeStatus.OK, "ping successful", pingDuration(start));
            }
            response.setStatusCode(HttpStatus.SC_OK);
        } catch (final TimeoutException te) {
            future.cancel(true);
            if (wantsBody) {
                setResponseContent(response, ProbeStatus.ERROR, "ping timeout", pingDuration(start));
            }
            response.setStatusCode(HttpStatus.SC_GATEWAY_TIMEOUT);
        } catch (final ExecutionException ee) {
            if (wantsBody) {
                setResponseContent(response, ProbeStatus.ERROR, getErrorDescription(ee), pingDuration(start));
            }
            response.setStatusCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (wantsBody) {
                setResponseContent(response, ProbeStatus.ERROR, "interrupted", pingDuration(start));
            }
            response.setStatusCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
        }
    }

    private String getErrorDescription(final ExecutionException ee) {
        return ee.getCause() != null
            ? ee.getCause().getMessage()
            : ee.getMessage();
    }

    private long pingDuration(final long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private void setResponseContent(final JsonResponse response, final ProbeStatus status, final String description,
            final long pingMs) {
        final JsonObject out = new JsonObject();
        out.addProperty(STATUS, status.toString());
        if (description != null) {
            out.addProperty(DESCRIPTION, description);
        }
        out.addProperty(LATENCY_IN_MILLIS, pingMs);
        response.setContent(out);
    }

    private enum ProbeStatus {
        OK("ok"), ERROR("error");

        private final String value;

        ProbeStatus(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
