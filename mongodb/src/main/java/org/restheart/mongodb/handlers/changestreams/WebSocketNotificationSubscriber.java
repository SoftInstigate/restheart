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
package org.restheart.mongodb.handlers.changestreams;

import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author omartrasatti
 */
public class WebSocketNotificationSubscriber implements Subscriber<ChangeStreamNotification> {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(WebSocketNotificationSubscriber.class);

    private Subscription sub;

    public WebSocketNotificationSubscriber() {

    }

    @Override
    public void onSubscribe(final Subscription s) {
        s.request(Long.MAX_VALUE);
        this.sub = s;
    }

    @Override
    public void onNext(ChangeStreamNotification notification) {
        Set<ChangeStreamWebSocketSession> sessions
                = GuavaHashMultimapSingleton.get(notification.getSessionKey());

        Set<ChangeStreamWebSocketSession> sessionsInError = Collections
                .newSetFromMap(new ConcurrentHashMap<>());

        sessions.stream().forEach(session -> {
            this.sendNotification(session, notification.getNotificationMessage(), sessionsInError);
        });

        sessionsInError.parallelStream().forEach(sessionInError -> {
            GuavaHashMultimapSingleton.remove(notification.getSessionKey(), sessionInError);
        });
    }

    @Override
    public void onError(final Throwable t) {
        LOGGER.warn("Error sending stream notification: " + t.getMessage());
    }

    @Override
    public void onComplete() {
        LOGGER.trace("Notification subscription completed");
    }

    public void stop() {
        this.sub.cancel();
    }

    private synchronized void sendNotification(ChangeStreamWebSocketSession session, String notificationMessage, Set<ChangeStreamWebSocketSession> sessionsInError) {
        WebSockets.sendText(notificationMessage, session.getChannel(), new WebSocketCallback<Void>() {

            @Override
            public void complete(final WebSocketChannel channel, Void context) {

            }

            @Override
            public void onError(final WebSocketChannel channel, Void context, Throwable throwable) {
                sessionsInError.add(session);
            }
        });
    }
}
