/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import com.mongodb.client.model.changestream.FullDocument;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.QueryNotFoundException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.RHMongoClients;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.mongodb.utils.StagesInterpolator.STAGE_OPERATOR;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final HttpHandler WEBSOCKET_HANDSHAKE_HANDLER = Handlers.websocket(new ChangeStreamWebsocketCallback());

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

                startStream(exchange);

                WEBSOCKET_HANDSHAKE_HANDLER.handleRequest(exchange);
            } else {
                response.setInError(HttpStatus.SC_BAD_REQUEST, "The stream connection requires WebSocket, no 'Upgrade' or 'Connection' request header found");

                next(exchange);
            }
        } catch (QueryNotFoundException ex) {
            response.setInError(HttpStatus.SC_NOT_FOUND, "Stream does not exist");

            LOGGER.debug("Requested stream {} does not exist", request.getUnmappedRequestUri());

            next(exchange);
        } catch (QueryVariableNotBoundException ex) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, ex.getMessage());

            LOGGER.warn("Cannot open stream connection, "
                    + "the request does not specify the required variables "
                    + "in the avars query paramter: {}",
                    ex.getMessage());

            next(exchange);
        } catch (IllegalStateException ise) {
            if (ise.getMessage() != null && ise.getMessage().contains("transport does not support HTTP upgrade")) {
                var error = "Cannot open stream connection: the AJP listener does not support WebSocket";

                LOGGER.warn(error);

                response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, error);
            }
        } catch (Throwable t) {
            LOGGER.error("Error handling the change stream request", t);
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
        String changesStreamOperation = request.getChangeStreamOperation();

        List<ChangeStreamOperation> streams = ChangeStreamOperation.getFromJson(request.getCollectionProps());

        Optional<ChangeStreamOperation> _query = streams
            .stream()
            .filter(q -> q.getUri().equals(changesStreamOperation))
            .findFirst();

        if (!_query.isPresent()) {
            throw new QueryNotFoundException("Stream " + request.getUnmappedRequestUri() + "  does not exist");
        }

        ChangeStreamOperation pipeline = _query.get();

        List<BsonDocument> resolvedStages = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, pipeline.getStages(), request.getAggregationVars());
        return resolvedStages;
    }

    private boolean startStream(HttpServerExchange exchange) throws QueryVariableNotBoundException, QueryNotFoundException, InvalidMetadataException {
        var streamKey = new SessionKey(exchange);
        var request = MongoRequest.of(exchange);

        List<BsonDocument> resolvedStages = getResolvedStagesAsList(request);

        if (!ChangeStreamsRegistry.getInstance().containsKey(streamKey)) {
            ChangeStreamsRegistry.getInstance().put(streamKey, new SessionInfo(MongoRequest.of(exchange)));

            RHMongoClients.mclientReactive()
                .getDatabase(request.getDBName())
                .getCollection(request.getCollectionName())
                .watch(resolvedStages)
                .fullDocument(FullDocument.UPDATE_LOOKUP)
                .subscribe(new ChangeStreamSubscriber(streamKey,
                    resolvedStages,
                    request.getDBName(),
                    request.getCollectionName()));

            return true;
        } else {
            return false;
        }
    }
}
