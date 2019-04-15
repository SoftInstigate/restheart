/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.stream;

import com.mongodb.client.ChangeStreamIterable;
import io.undertow.Handlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.BsonDocument;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.metadata.InvalidMetadataException;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

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

    private static WebSocketProtocolHandshakeHandler WEBSOCKET_HANDLER = Handlers
            .websocket(new ChangeStreamWebsocketCallback());

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

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {

        if (context.isInError()) {
            next(exchange, context);
            return;
        }
        
        if (isWebSocketHandshakeRequest(exchange)) {

            if (!isRequestedChangeStreamAlreadyOpen(exchange.getRelativePath(), context)) {
                openChangeStream(exchange.getRelativePath(), context);
            }

            exchange.putAttachment(AVARS_ATTACHMENT_KEY, getResolvedStagesAsList(context));

            WEBSOCKET_HANDLER.handleRequest(exchange);

        } else {
            ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_BAD_REQUEST,
                    "No Upgrade header has been found into request headers");
            next(exchange, context);
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

    private boolean isRequestedChangeStreamAlreadyOpen(String changeStreamUri, RequestContext context) throws QueryVariableNotBoundException, Exception {

        boolean result = false;

        Set<CacheableChangeStreamKey> openedChangeStreams
                = ChangeStreamCacheManagerSingleton.getChangeStreamsKeySet();

        CacheableChangeStreamKey requestedStreamKey
                = new CacheableChangeStreamKey(changeStreamUri, getResolvedStagesAsList(context));

        for (CacheableChangeStreamKey key : openedChangeStreams) {

            if (key.getAVars().equals(requestedStreamKey.getAVars())
                    && key.getUrl().equals(requestedStreamKey.getUrl())) {
                result = true;
            }
        }

        return result;
    }

    private List<BsonDocument> getResolvedStagesAsList(RequestContext context) throws InvalidMetadataException, QueryVariableNotBoundException, Exception {

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

    private ChangeStreamIterable initChangeStream(RequestContext context) throws QueryVariableNotBoundException, Exception {

        return getDatabase()
                .getCollection(context.getDBName(), context.getCollectionName())
                .watch(
                        getResolvedStagesAsList(context)
                );

    }

    private void openChangeStream(String changeStreamUri, RequestContext context) throws Exception {

        ChangeStreamCacheManagerSingleton.cacheChangeStreamCursor(
                new CacheableChangeStreamKey(changeStreamUri,
                        getResolvedStagesAsList(context)),
                new CacheableChangeStreamCursor(
                        initChangeStream(context).iterator())
        );
    }
}
