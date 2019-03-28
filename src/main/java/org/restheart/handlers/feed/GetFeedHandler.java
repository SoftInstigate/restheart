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
package org.restheart.handlers.feed;

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
public class GetFeedHandler extends PipedHttpHandler {

    public GetFeedHandler() {
        super();
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public GetFeedHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange,
            RequestContext context) throws Exception {
        System.out.println(context.getUnmappedRequestUri());
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        if (context.getFeedIdentifier() != null) {
            
            if (exchange.getRequestHeaders()
                    .get("connection")
                        .getFirst()
                            .toLowerCase()
                                .equals("upgrade")) {
                System.out.println(exchange.getRequestHeaders().toString());
                String wsUriPath = getWsUri(context);

                CacheableFeed feed
                        = CacheManagerSingleton
                                .retrieveWebSocket(wsUriPath);

                WebSocketProtocolHandshakeHandler wsHandler
                        = feed.getHandshakeHandler();

                if (wsHandler != null) {

                    wsHandler.handleRequest(exchange);

                } else {

                    ResponseHelper.endExchangeWithMessage(
                            exchange,
                            context,
                            HttpStatus.SC_NOT_FOUND,
                            "WebSocket resource hasnt been initialized");

                    next(exchange, context);
                }
            } else {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "No Upgrade header has been found into request headers");

                next(exchange, context);
            }

        } else {

            ArrayList<BsonDocument> data = new ArrayList<>();

            Set<String> wsResoucesUriSet = CacheManagerSingleton
                    .retrieveResourcesUriSet();

            if (!wsResoucesUriSet.isEmpty()) {
                // TODO ritornare lista delle uri dei ws in cache
                for (String resource : wsResoucesUriSet) {
                    CacheableFeed feedResource
                            = CacheManagerSingleton
                                    .retrieveWebSocket(resource);
                    
                    List<BsonDocument> aVars = feedResource.getAVars();
                    
                    String jsonString = "{'" + getResourceIdentifier(resource) + "': "+ aVars.toString() +"}";
                    if (checkIfRequestedFeedResourceUri(resource, context)) {
                        data.add(BsonDocument.parse(jsonString));
                    }

                }

                long size = data.size();

                context.setResponseContent(new FeedResultRepresentationFactory()
                        .getRepresentation(exchange, context, data, size)
                        .asBsonDocument());
                context.setResponseStatusCode(HttpStatus.SC_OK);
                
                next(exchange, context);

            } else {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_NOT_FOUND,
                        "No feeds are notifying for this feedOperation");

                next(exchange, context);
            }

        }

    }

    private boolean checkIfRequestedFeedResourceUri(String URI, RequestContext context) {
        String[] uriPath = URI.split("/");
        return uriPath[4].equals(context.getFeedOperation());
    }

    private String getResourceIdentifier(String URI) {
        String[] uriPath = URI.split("/");
        return uriPath[5];
    }
    
    private String getWsUri(RequestContext context) {

        String result = "/" + context.getDBName()
                + "/" + context.getCollectionName()
                + "/_feeds/"
                + context.getFeedOperation() + "/"
                + context.getFeedIdentifier();

        return result;
    }

}
