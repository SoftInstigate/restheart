/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.restheart.handlers.stream;

import com.mongodb.client.model.changestream.FullDocument;
import com.restheart.db.MongoDBReactiveClientSingleton;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.json.JsonMode;
import org.bson.BsonDocument;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.metadata.InvalidMetadataException;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */
public class GetChangeStreamHandler extends PipedHttpHandler {

    private final String CONNECTION_HEADER_KEY = "connection";
    private final String CONNECTION_HEADER_VALUE = "upgrade";
    private final String UPGRADE_HEADER_KEY = "upgrade";
    private final String UPGRADE_HEADER_VALUE = "websocket";

    public static final Set<SessionKey> OPENED_STREAMS = Collections.newSetFromMap(new ConcurrentHashMap<SessionKey, Boolean>());
    private static final Logger LOGGER = LoggerFactory.getLogger(GetChangeStreamHandler.class);
    private static HttpHandler WEBSOCKET_HANDSHAKE_HANDLER
            = Handlers.websocket(new ChangeStreamWebsocketCallback());

    public static final AttachmentKey<BsonDocument> AVARS_ATTACHMENT_KEY = AttachmentKey.create(BsonDocument.class);
    public static final AttachmentKey<JsonMode> JSON_MODE_ATTACHMENT_KEY = AttachmentKey.create(JsonMode.class);

    public GetChangeStreamHandler() {
        super();
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public GetChangeStreamHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context)
            throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        try {
            if (isWebSocketHandshakeRequest(exchange)) {
                exchange.putAttachment(JSON_MODE_ATTACHMENT_KEY, context.getJsonMode());
                exchange.putAttachment(AVARS_ATTACHMENT_KEY, context.getAggreationVars());

                startStream(exchange, context);

                WEBSOCKET_HANDSHAKE_HANDLER.handleRequest(exchange);
            } else {
                ResponseHelper.endExchangeWithMessage(exchange, context,
                        HttpStatus.SC_BAD_REQUEST,
                        "The stream connection requires WebSocket, "
                        + "no 'Upgrade' request header found");

                next(exchange, context);
            }
        } catch (QueryNotFoundException ex) {
            ResponseHelper.endExchangeWithMessage(exchange, context,
                    HttpStatus.SC_NOT_FOUND,
                    "Stream does not exist");
            
            LOGGER.debug("Requested stream {} does not exist", context.getUnmappedRequestUri());
            
            next(exchange, context);
        } catch (QueryVariableNotBoundException ex) {
            ResponseHelper.endExchangeWithMessage(exchange, context,
                    HttpStatus.SC_BAD_REQUEST,
                    ex.getMessage());

            LOGGER.warn("Cannot open stream connection, "
                    + "the request does not specify the required variables "
                    + "in the avars query paramter: {}",
                    ex.getMessage());

            next(exchange, context);
        } catch (IllegalStateException ise) {
            if (ise.getMessage() != null
                    && ise.getMessage()
                            .contains("transport does not support HTTP upgrade")) {

                var error = "Cannot open stream connection: "
                        + "the AJP listener does not support WebSocket";

                LOGGER.warn(error);

                ResponseHelper.endExchangeWithMessage(exchange,
                        context,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR, error);
            }
        } catch (Throwable t) {
            ResponseHelper.endExchangeWithMessage(exchange,
                    context,
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    t.getMessage());
        }
    }

    private boolean isWebSocketHandshakeRequest(HttpServerExchange exchange) {
        return exchange.getRequestHeaders()
                .get(CONNECTION_HEADER_KEY)
                .getFirst().toLowerCase()
                .equals(CONNECTION_HEADER_VALUE)
                && exchange.getRequestHeaders()
                        .get(UPGRADE_HEADER_KEY)
                        .getFirst().toLowerCase()
                        .equals(UPGRADE_HEADER_VALUE);
    }

    private List<BsonDocument> getResolvedStagesAsList(RequestContext context)
            throws InvalidMetadataException,
            QueryVariableNotBoundException,
            QueryNotFoundException {
        String changesStreamOperation = context.getChangeStreamOperation();

        List<ChangeStreamOperation> streams = ChangeStreamOperation
                .getFromJson(context.getCollectionProps());

        Optional<ChangeStreamOperation> _query = streams
                .stream()
                .filter(q -> q.getUri().equals(changesStreamOperation))
                .findFirst();

        if (!_query.isPresent()) {
            throw new QueryNotFoundException("Stream " 
                    + context.getUnmappedRequestUri()
                    + "  does not exist");
        }

        ChangeStreamOperation pipeline = _query.get();

        List<BsonDocument> resolvedStages = pipeline
                .getResolvedStagesAsList(context.getAggreationVars());
        return resolvedStages;
    }

    private boolean startStream(HttpServerExchange exchange, RequestContext context)
            throws QueryVariableNotBoundException,
            QueryNotFoundException,
            InvalidMetadataException {
        SessionKey streamKey = new SessionKey(exchange);

        List<BsonDocument> resolvedStages = getResolvedStagesAsList(context);

        if (OPENED_STREAMS.add(streamKey)) {
            MongoDBReactiveClientSingleton
                    .getInstance()
                    .getClient()
                    .getDatabase(context.getDBName())
                    .getCollection(context.getCollectionName())
                    .watch(resolvedStages)
                    .fullDocument(FullDocument.UPDATE_LOOKUP)
                    .subscribe(new ChangeStreamSubscriber(streamKey));

            return true;
        } else {
            return false;
        }
    }
}
