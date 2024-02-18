/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.QueryNotFoundException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.handlers.PipelinedHandler;
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
import io.undertow.util.AttachmentKey;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */
public class GetChangeStreamHandler extends PipelinedHandler {
    private final String CONNECTION_HEADER_KEY = "connection";
    private final String CONNECTION_HEADER_VALUE = "upgrade";
    private final String UPGRADE_HEADER_KEY = "upgrade";
    private final String UPGRADE_HEADER_VALUE = "websocket";

    private static final Logger LOGGER = LoggerFactory.getLogger(GetChangeStreamHandler.class);
    private static final HttpHandler WEBSOCKET_HANDLER = Handlers.websocket((exchange, channel) -> {
        var csKey = new ChangeStreamWorkerKey(exchange);
        var csw$ = ChangeStreamWorkers.getInstance().get(csKey);

        if (csw$.isPresent()) {
            var wss = new WebSocketSession(channel);
            csw$.get().websocketSessions().add(wss);
            LOGGER.debug("New Change Stream WebSocket session, sessionkey={} for changeStreamKey={}", wss.getId(), csKey);
        } else {
            LOGGER.error("Cannot find Change Stream Worker changeStreamKey={}", csKey);
            try {
                channel.close();
            } catch (IOException e) {
            }
        }
    });

    public static final AttachmentKey<BsonDocument> AVARS_ATTACHMENT_KEY = AttachmentKey.create(BsonDocument.class);
    public static final AttachmentKey<JsonMode> JSON_MODE_ATTACHMENT_KEY = AttachmentKey.create(JsonMode.class);

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        try {
            if (isWebSocketHandshakeRequest(exchange)) {
                exchange.putAttachment(JSON_MODE_ATTACHMENT_KEY, request.getJsonMode());
                exchange.putAttachment(AVARS_ATTACHMENT_KEY, request.getAggregationVars());

                initChangeStreamWorker(exchange);

                WEBSOCKET_HANDLER.handleRequest(exchange);
            } else {
                response.setInError(HttpStatus.SC_BAD_REQUEST, "Change Stream requires WebSocket, no 'Upgrade' or 'Connection' request header found");

                next(exchange);
            }
        } catch (QueryNotFoundException ex) {
            response.setInError(HttpStatus.SC_NOT_FOUND, "Change Stream does not exist");

            LOGGER.debug("Requested Change Stream {} does not exist", request.getUnmappedRequestUri());

            next(exchange);
        } catch (QueryVariableNotBoundException ex) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, ex.getMessage());

            LOGGER.warn("Cannot open change stream, "
                    + "the request does not specify the required variables "
                    + "in the avars query paramter: {}",
                    ex.getMessage());

            next(exchange);
        } catch (IllegalStateException ise) {
            if (ise.getMessage() != null && ise.getMessage().contains("transport does not support HTTP upgrade")) {
                var error = "Cannot open change stream: the AJP listener does not support WebSocket";

                LOGGER.warn(error);

                response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, error);
            }
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

    private List<BsonDocument> getResolvedStagesAsList(MongoRequest request) throws InvalidMetadataException, QueryVariableNotBoundException, QueryNotFoundException {
        var changesStreamOperation = request.getChangeStreamOperation();

        var streams = ChangeStreamOperation.getFromJson(request.getCollectionProps());

        var _query = streams
            .stream()
            .filter(q -> q.getUri().equals(changesStreamOperation))
            .findFirst();

        if (!_query.isPresent()) {
            throw new QueryNotFoundException("Stream " + request.getUnmappedRequestUri() + "  does not exist");
        }

        var pipeline = _query.get();

        var resolvedStages = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, pipeline.getStages(), request.getAggregationVars());
        return resolvedStages;
    }

    /**
     * Initiate a `ChangeStreamWorker` thread to monitor change streams and relay updates to WebSocket clients.
     *
     * @param exchange
     *
     * @throws QueryVariableNotBoundException
     * @throws QueryNotFoundException
     * @throws InvalidMetadataException
     */
    private synchronized void initChangeStreamWorker(HttpServerExchange exchange) throws QueryVariableNotBoundException, QueryNotFoundException, InvalidMetadataException {
        var csKey = new ChangeStreamWorkerKey(exchange);
        var request = MongoRequest.of(exchange);

        var resolvedStages = getResolvedStagesAsList(request);

        var existingChangeSreamWorker$ = ChangeStreamWorkers.getInstance().get(csKey);

        if (existingChangeSreamWorker$.isEmpty()) {
            var changeStreamWorker = (new ChangeStreamWorker(csKey,
                resolvedStages,
                request.getDBName(),
                request.getCollectionName()));

            ChangeStreamWorkers.getInstance().put(changeStreamWorker);

            ThreadsUtils.virtualThreadsExecutor().execute(changeStreamWorker);

            LOGGER.debug("Started Change Stream Worker, {}", csKey);
        } else {
            LOGGER.debug("Change Stream Worker already exists, {}", csKey);
        }
    }
}
