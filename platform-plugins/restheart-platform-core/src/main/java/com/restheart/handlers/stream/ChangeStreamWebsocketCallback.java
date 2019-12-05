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

import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
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
        JsonMode jsonMode = exchange.getAttachment(GetChangeStreamHandler.JSON_MODE_ATTACHMENT_KEY);

        if (!exchange.getQueryString().isEmpty()) {
            url = exchange.getRequestURI().replace(exchange.getQueryString(), "");
            url = url.substring(0, url.length() - 1);
        } else {
            url = exchange.getRequestURI();
        }

        String sessionKey;

        if (aVars == null) {
            sessionKey = url + (jsonMode != null
                    ? "?jsonMode=" + jsonMode : "");

        } else {
            sessionKey = url + "?avars=" + aVars.toJson() + (jsonMode != null
                    ? "&jsonMode=" + jsonMode : "");

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
