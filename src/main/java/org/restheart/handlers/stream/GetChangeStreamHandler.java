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

import io.undertow.Handlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */
public class GetChangeStreamHandler extends PipedHttpHandler {
    private static WebSocketProtocolHandshakeHandler WEBSOCKET_HANDLER = Handlers
            .websocket(new ChangeStreamWebsocketCallback());

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
        System.out.println(context.getUnmappedRequestUri());
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        if (context.getChangeStreamIdentifier() != null) {

            if (exchange.getRequestHeaders().get("connection").getFirst().toLowerCase().equals("upgrade")) {
                String changeStreamUri = getWsUri(context);

                if (changeStreamIsOpen(changeStreamUri)) {
                    WEBSOCKET_HANDLER.handleRequest(exchange);
                } else {
                    ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_NOT_FOUND,
                            "WebSocket resource hasnt been initialized");
                    next(exchange, context);
                }
            } else {
                ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_BAD_REQUEST,
                        "No Upgrade header has been found into request headers");
                next(exchange, context);
            }

        } else {
            ArrayList<BsonDocument> data = getOpenedChangeStreamsRequestData(context);
            long size = data.size();

            if (size == 0) {
                ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_NOT_FOUND,
                        "No streams are notifying for this changeStreamOperation");
                next(exchange, context);
            }

            context.setResponseContent(new ChangeStreamResultRepresentationFactory()
                    .getRepresentation(exchange, context, data, size).asBsonDocument());
            context.setResponseStatusCode(HttpStatus.SC_OK);
            next(exchange, context);

        }

    }

    private boolean resourceBelongsToChangeStreamOperation(String URI, RequestContext context) {
        String[] uriPath = URI.split("/");
        return uriPath[4].equals(context.getChangeStreamOperation());
    }

    private String getResourceIdentifier(String URI) {
        String[] uriPath = URI.split("/");
        return uriPath[5];
    }

    private String getWsUri(RequestContext context) {

        String result = "/" + context.getDBName() + "/" + context.getCollectionName() + "/_streams/"
                + context.getChangeStreamOperation() + "/" + context.getChangeStreamIdentifier();

        return result;
    }

    private boolean changeStreamIsOpen(String changeStreamUri) {

        Set<String> openedChangeStreamsUriSet = CacheManagerSingleton.getChangeStreamsUriSet();

        return openedChangeStreamsUriSet.contains(changeStreamUri);
    }

    private ArrayList<BsonDocument> getOpenedChangeStreamsRequestData(RequestContext context) {

        ArrayList<BsonDocument> data = new ArrayList<>();
        Set<String> changeStreamUriSet = CacheManagerSingleton.getChangeStreamsUriSet();

        if (!changeStreamUriSet.isEmpty()) {

            for (String resource : changeStreamUriSet) {

                if (resourceBelongsToChangeStreamOperation(resource, context)) {

                    CacheableChangesStreamCursor cachedChangeStreamIterable = CacheManagerSingleton
                            .getCachedChangeStreamIterable(resource);

                    List<BsonDocument> aVars = cachedChangeStreamIterable.getAVars();

                    String jsonString = "{'" + getResourceIdentifier(resource) + "': " + aVars.toString() + "}";

                    data.add(BsonDocument.parse(jsonString));
                }
            }
        }
        return data;
    }
}
