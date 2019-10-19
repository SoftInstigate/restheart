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

import com.restheart.db.MongoDBReactiveClientSingleton;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    public static final Set<String> OPENED_STREAMS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Logger LOGGER = LoggerFactory.getLogger(GetChangeStreamHandler.class);
    private static HttpHandler WEBSOCKET_HANDSHAKE_HANDLER
            = Handlers.websocket(new ChangeStreamWebsocketCallback());

    public static final AttachmentKey<List<BsonDocument>> AVARS_ATTACHMENT_KEY = AttachmentKey.create(List.class);

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
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {

        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        try {
            if (isWebSocketHandshakeRequest(exchange)) {

                startStream(exchange, context);

                exchange.setQueryString(
                        encodeQueryString(exchange.getQueryString()));

                WEBSOCKET_HANDSHAKE_HANDLER.handleRequest(exchange);

            } else {
                ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_BAD_REQUEST,
                        "No Upgrade header has been found into request headers");
                next(exchange, context);
            }
        } catch (QueryNotFoundException ex) {
            ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_NOT_FOUND,
                    "query does not exist");
            next(exchange, context);
        }

    }

    private String encodeQueryString(String queryString) {

        String result = null;
        String charset = java.nio.charset.StandardCharsets.UTF_8.toString();

        try {
            result = java.net.URLEncoder.encode(
                    java.net.URLDecoder.decode(queryString, charset), charset);
        } catch (UnsupportedEncodingException e) {
            LOGGER.error(e.getMessage() + "; Exception thrown encoding queryString");
        }

        return result;
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

    private List<BsonDocument> getResolvedStagesAsList(RequestContext context) throws InvalidMetadataException, QueryVariableNotBoundException, QueryNotFoundException {

        String changesStreamOperation = context.getChangeStreamOperation();

        List<ChangeStreamOperation> streams = ChangeStreamOperation.getFromJson(context.getCollectionProps());
        Optional<ChangeStreamOperation> _query = streams.stream().filter(q -> q.getUri().equals(changesStreamOperation))
                .findFirst();

        if (!_query.isPresent()) {
            throw new QueryNotFoundException("Query does not exist");
        }

        ChangeStreamOperation pipeline = _query.get();

        List<BsonDocument> resolvedStages = pipeline.getResolvedStagesAsList(context.getAggreationVars());
        return resolvedStages;
    }


    private boolean startStream(HttpServerExchange exchange, RequestContext context) throws QueryVariableNotBoundException, QueryNotFoundException, InvalidMetadataException {

        String streamKey;
        
        if(context.getAggreationVars() != null) {
            streamKey = exchange.getRelativePath()
                + "?avars="
                + context.getAggreationVars().toJson();
        } else {
            streamKey = exchange.getRelativePath();
        }

        if (OPENED_STREAMS.add(streamKey)) {

            MongoDBReactiveClientSingleton
                    .getInstance()
                    .getClient()
                    .getDatabase(context.getDBName())
                    .getCollection(context.getCollectionName())
                    .watch(getResolvedStagesAsList(context))
                    .subscribe(new ChangeStreamSubscriber(streamKey));

        } else {
            return false;
        }
        return true;
    }

}
