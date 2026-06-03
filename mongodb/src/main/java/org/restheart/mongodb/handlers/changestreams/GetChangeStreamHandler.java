/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.handlers.changestreams;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.QueryNotFoundException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.security.AggregationPipelineSecurityChecker;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.mongodb.utils.StagesInterpolator.STAGE_OPERATOR;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;

/**
 * Handles change stream requests over WebSocket or SSE.
 *
 * <p>Branches on the request type:
 * <ul>
 *   <li>{@code Upgrade: websocket} — existing WebSocket path (unchanged)</li>
 *   <li>{@code Accept: text/event-stream} — new SSE path</li>
 *   <li>neither — {@code 400 Bad Request}</li>
 * </ul>
 *
 * <p>Both paths share the same {@link ChangeStreamWorker} cursor-sharing
 * infrastructure: one MongoDB cursor per unique {@link ChangeStreamWorkerKey}
 * fans out to all connected WebSocket and SSE sessions.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class GetChangeStreamHandler extends PipelinedHandler {
    private static final String CONNECTION_HEADER_KEY = "connection";
    private static final String CONNECTION_HEADER_VALUE = "upgrade";
    private static final String UPGRADE_HEADER_KEY = "upgrade";
    private static final String UPGRADE_HEADER_VALUE = "websocket";

    private static final Logger LOGGER = LoggerFactory.getLogger(GetChangeStreamHandler.class);
    private final AggregationPipelineSecurityChecker securityChecker;

    public static final AttachmentKey<BsonDocument> AVARS_ATTACHMENT_KEY = AttachmentKey.create(BsonDocument.class);
    public static final AttachmentKey<JsonMode>     JSON_MODE_ATTACHMENT_KEY = AttachmentKey.create(JsonMode.class);
    /** Bound query-parameter variables for {@code notify_when} filtering, keyed by variable name. */
    public static final AttachmentKey<Map<String, String>> BOUND_VARS_EXCHANGE_KEY = AttachmentKey.create(Map.class);

    private static final HttpHandler WEBSOCKET_HANDLER = Handlers.websocket((exchange, channel) -> {
        var csKey = new ChangeStreamWorkerKey(exchange);
        var csw$ = ChangeStreamWorkers.getInstance().get(csKey);

        if (csw$.isPresent()) {
            var csw = csw$.get();
            var boundVars = exchange.getAttachment(BOUND_VARS_EXCHANGE_KEY);
            var wss = new WebSocketSession(channel, csw, boundVars);
            csw.websocketSessions().add(wss);
            LOGGER.debug("New Change Stream WebSocket session, sessionkey={} for changeStreamKey={}", wss.getId(), csKey);
        } else {
            LOGGER.error("Cannot find Change Stream Worker changeStreamKey={}", csKey);
            try {
                channel.close();
            } catch (IOException ignored) {
                // nothing to do — channel is already gone
            }
        }
    });

    public GetChangeStreamHandler() {
        super();
        var config = MongoServiceConfiguration.get().getAggregationSecurityConfiguration();
        this.securityChecker = new AggregationPipelineSecurityChecker(config);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        try {
            // Resolve the stream operation early — needed for notify_when and bound vars
            var operation = findOperation(request);
            var evaluator = NotifyWhenEvaluator.from(operation.getNotifyWhen());
            var boundVars = extractBoundVars(exchange, evaluator);
            exchange.putAttachment(BOUND_VARS_EXCHANGE_KEY, boundVars);

            if (isWebSocketHandshakeRequest(exchange)) {
                exchange.putAttachment(JSON_MODE_ATTACHMENT_KEY, request.getJsonMode());

                var _avars = request.getAggregationVars();
                if (_avars == null) {
                    _avars = new BsonDocument();
                }
                StagesInterpolator.injectAvars(request, _avars);
                exchange.putAttachment(AVARS_ATTACHMENT_KEY, _avars);

                initChangeStreamWorker(exchange, null, evaluator);
                WEBSOCKET_HANDLER.handleRequest(exchange);

            } else if (isSseRequest(exchange)) {
                exchange.putAttachment(JSON_MODE_ATTACHMENT_KEY, request.getJsonMode());

                var _avars = request.getAggregationVars();
                if (_avars == null) {
                    _avars = new BsonDocument();
                }
                StagesInterpolator.injectAvars(request, _avars);
                exchange.putAttachment(AVARS_ATTACHMENT_KEY, _avars);

                // Last-Event-ID is not supported when notify_when is defined
                BsonDocument resumeToken = null;
                if (evaluator != null) {
                    if (exchange.getRequestHeaders().getFirst("Last-Event-ID") != null) {
                        LOGGER.warn("Last-Event-ID is not supported when notify_when is defined; ignoring");
                    }
                } else {
                    resumeToken = parseResumeToken(exchange);
                }

                initChangeStreamWorker(exchange, resumeToken, evaluator);
                sseHandlerFor(exchange).handleRequest(exchange);

            } else {
                response.setInError(HttpStatus.SC_BAD_REQUEST,
                    "Change Stream requires WebSocket or SSE — send either 'Upgrade: websocket' or 'Accept: text/event-stream'");
                next(exchange);
            }
        } catch (QueryNotFoundException ex) {
            response.setInError(HttpStatus.SC_NOT_FOUND, "Change Stream does not exist");
            LOGGER.debug("Requested Change Stream {} does not exist", request.getMongoResourceUri());
            next(exchange);
        } catch (QueryVariableNotBoundException ex) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, ex.getMessage());
            LOGGER.warn("Cannot open change stream, the request does not specify the required variables "
                    + "in the avars query parameter: {}", ex.getMessage());
            next(exchange);
        } catch (IllegalStateException ise) {
            if (ise.getMessage() != null && ise.getMessage().contains("transport does not support HTTP upgrade")) {
                var error = "Cannot open change stream: the AJP listener does not support WebSocket";
                LOGGER.warn(error);
                response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, error);
            }
        } catch(SecurityException se) {
            var error = "Cannot open change stream: " + se.getMessage();
            LOGGER.warn(error, se);
            response.setInError(HttpStatus.SC_FORBIDDEN, error);
        } catch (Throwable t) {
            LOGGER.error("Error handling the Change Stream request", t);
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, t.getMessage());
        }
    }

    private boolean isWebSocketHandshakeRequest(HttpServerExchange exchange) {
        var chVals = exchange.getRequestHeaders().get(CONNECTION_HEADER_KEY);
        var uhVals = exchange.getRequestHeaders().get(UPGRADE_HEADER_KEY);

        return chVals != null && uhVals != null &&
            Arrays.stream(chVals.toArray()).anyMatch(val -> val.toLowerCase().contains(CONNECTION_HEADER_VALUE)) &&
            Arrays.stream(uhVals.toArray()).anyMatch(val -> val.toLowerCase().contains(UPGRADE_HEADER_VALUE));
    }

    private boolean isSseRequest(HttpServerExchange exchange) {
        var accept = exchange.getRequestHeaders().getFirst(Headers.ACCEPT);
        return accept != null && accept.contains("text/event-stream");
    }

    /**
     * Returns a {@link ServerSentEventHandler} for the given exchange.
     *
     * <p>When a client connects, the callback looks up the already-initialised
     * {@link ChangeStreamWorker} by key, registers the SSE connection in
     * {@code sseSessions}, and adds a close task that removes the connection and
     * interrupts the worker when all sessions (WebSocket and SSE) are gone.
     */
    private static HttpHandler sseHandlerFor(HttpServerExchange exchange) {
        return new ServerSentEventHandler((connection, lastEventId) -> {
            // Attach bound vars so the worker can evaluate notify_when per-connection
            var boundVars = exchange.getAttachment(BOUND_VARS_EXCHANGE_KEY);
            connection.putAttachment(ChangeStreamWorker.BOUND_VARS_KEY, boundVars != null ? boundVars : Map.of());

            var csKey = new ChangeStreamWorkerKey(exchange);
            var csw$ = ChangeStreamWorkers.getInstance().get(csKey);

            if (csw$.isPresent()) {
                var csw = csw$.get();
                csw.sseSessions().add(connection);

                connection.addCloseTask(c -> {
                    csw.sseSessions().remove(connection);

                    if (csw.websocketSessions().isEmpty() && csw.sseSessions().isEmpty()
                            && csw.handlingVirtualThread() != null) {
                        LOGGER.debug("Terminating worker {} (last SSE session closed)", csw.handlingVirtualThread().getName());
                        csw.handlingVirtualThread().interrupt();
                    }
                });

                LOGGER.debug("New Change Stream SSE session for changeStreamKey={}", csKey);
            } else {
                LOGGER.error("Cannot find Change Stream Worker changeStreamKey={}", csKey);
                connection.shutdown();
            }
        });
    }

    /**
     * Parses the {@code Last-Event-ID} request header as a BSON resume token.
     * Returns {@code null} when the header is absent or cannot be parsed.
     */
    private BsonDocument parseResumeToken(HttpServerExchange exchange) {
        var lastEventId = exchange.getRequestHeaders().getFirst("Last-Event-ID");
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            return BsonDocument.parse(lastEventId);
        } catch (Exception e) {
            LOGGER.warn("Invalid Last-Event-ID resume token '{}', starting stream from head", lastEventId);
            return null;
        }
    }

    private List<BsonDocument> getResolvedStagesAsList(MongoRequest request)
            throws InvalidMetadataException, QueryVariableNotBoundException, QueryNotFoundException, SecurityException {
        var pipeline = findOperation(request);
        var avars = request.getExchange().getAttachment(GetChangeStreamHandler.AVARS_ATTACHMENT_KEY);
        var resolvedStages = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, pipeline.getStages(), avars);

        var stagesArray = new BsonArray();
        resolvedStages.forEach(stagesArray::add);
        securityChecker.validatePipelineOrThrow(stagesArray, request.getDBName());

        return resolvedStages;
    }

    /** Finds the matching {@link ChangeStreamOperation} for the current request. */
    private ChangeStreamOperation findOperation(MongoRequest request)
            throws InvalidMetadataException, QueryNotFoundException {
        var changesStreamOperation = request.getChangeStreamOperation();
        var streams = ChangeStreamOperation.getFromJson(request.getCollectionProps());

        return streams.stream()
                .filter(q -> q.getUri().equals(changesStreamOperation))
                .findFirst()
                .orElseThrow(() -> new QueryNotFoundException(
                        "Stream " + request.getMongoResourceUri() + " does not exist"));
    }

    /**
     * Extracts the single query-parameter variable required by the {@code notify_when}
     * evaluator. Returns an empty map when {@code evaluator} is {@code null} or the
     * parameter is absent.
     */
    private static Map<String, String> extractBoundVars(HttpServerExchange exchange, NotifyWhenEvaluator evaluator) {
        if (evaluator == null) return Map.of();
        var varName = evaluator.getVarName();
        var params  = exchange.getQueryParameters().get(varName);
        if (params == null || params.isEmpty()) return Map.of();
        return Map.of(varName, params.getFirst());
    }

    /**
     * Ensures a {@link ChangeStreamWorker} exists for the given exchange.
     *
     * <p>If a worker with the same {@link ChangeStreamWorkerKey} already exists it
     * is reused (the {@code resumeToken} and {@code evaluator} are ignored — the
     * shared cursor continues from its current position). Otherwise a new worker is
     * created and started.
     */
    private synchronized void initChangeStreamWorker(HttpServerExchange exchange,
            BsonDocument resumeToken, NotifyWhenEvaluator evaluator)
            throws QueryVariableNotBoundException, QueryNotFoundException, InvalidMetadataException {
        var csKey = new ChangeStreamWorkerKey(exchange);
        var request = MongoRequest.of(exchange);
        var resolvedStages = getResolvedStagesAsList(request);

        var existingWorker$ = ChangeStreamWorkers.getInstance().get(csKey);

        if (existingWorker$.isEmpty()) {
            var changeStreamWorker = new ChangeStreamWorker(csKey,
                resolvedStages,
                request.getDBName(),
                request.getCollectionName(),
                resumeToken,
                evaluator);

            ChangeStreamWorkers.getInstance().put(changeStreamWorker);
            ThreadsUtils.virtualThreadsExecutor().execute(changeStreamWorker);
            LOGGER.debug("Started Change Stream Worker, {}", csKey);
        } else {
            LOGGER.debug("Change Stream Worker already exists, {}", csKey);
        }
    }
}
