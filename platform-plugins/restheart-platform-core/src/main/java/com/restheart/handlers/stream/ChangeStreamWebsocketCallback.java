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
package com.restheart.handlers.stream;

import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import org.bson.BsonDocument;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.SubmissionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 *
 */
public class ChangeStreamWebsocketCallback implements WebSocketConnectionCallback {

    public static final SubmissionPublisher<ChangeStreamNotification> NOTIFICATION_PUBLISHER = new SubmissionPublisher<>();

    private static final Logger LOGGER
            = LoggerFactory.getLogger(ChangeStreamWebsocketCallback.class);

    public ChangeStreamWebsocketCallback() {
        NOTIFICATION_PUBLISHER.subscribe(new WebSocketNotificationSubscriber());
    }

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {

        String url;
        BsonDocument aVars = getQueryStringAvars(exchange.getQueryString());
        
        if (!exchange.getQueryString().isEmpty()) {
            url = exchange.getRequestURI().replace(exchange.getQueryString(), "");
            url = url.substring(0, url.length() - 1);
        } else {
            url = exchange.getRequestURI();
        }

        String sessionKey;

        if (aVars == null) {
            sessionKey = url;
        } else {
            sessionKey = url + "?avars=" + aVars.toJson();
        }

        ChangeStreamWebSocketSession newSession
                = createSession(channel, sessionKey);
        
        GuavaHashMultimapSingleton.addSession(sessionKey, newSession);
    }

    private ChangeStreamWebSocketSession createSession(WebSocketChannel channel, String sessionKey) {

        ChangeStreamWebSocketSession newSession = new ChangeStreamWebSocketSession(
                channel,
                sessionKey
        );

        LOGGER.info("Websocket connection established; [sessionId]: " + newSession.getId());

        return newSession;
    }

    private static BsonDocument getQueryStringAvars(String queryString) {

        Map<String, String> queryPairsMap = null;
        BsonDocument avarsDocument = null;
        if (queryString != null && queryString.length() > 0) {
            try {
                queryPairsMap = queryStringToMap(URLDecoder.decode(queryString, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                System.out.println(e.getMessage() + "while parsing queryString to map");
            }

            if (queryPairsMap.get("avars") != null) {
                avarsDocument = BsonDocument.parse(queryPairsMap.get("avars"));
            }

        }

        return avarsDocument;
    }

    private static Map<String, String> queryStringToMap(String query) throws UnsupportedEncodingException {

        Map<String, String> query_pairs = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return query_pairs;
    }

}
