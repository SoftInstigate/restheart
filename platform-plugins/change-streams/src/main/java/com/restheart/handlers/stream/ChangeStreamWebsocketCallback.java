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
        var sessionKey = new SessionKey(exchange);
        
        ChangeStreamWebSocketSession newSession
                = createSession(channel, sessionKey);

        LOGGER.debug("New stream connection, sessionkey={}", sessionKey);
        
        GuavaHashMultimapSingleton.add(sessionKey, newSession);
    }
    
    private ChangeStreamWebSocketSession createSession(WebSocketChannel channel, SessionKey sessionKey) {
        ChangeStreamWebSocketSession newSession = new ChangeStreamWebSocketSession(
                channel, sessionKey);

        return newSession;
    }
}
