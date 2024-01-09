/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

import java.io.IOException;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketNotificationSubscriber.class);

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
        Set<ChangeStreamWebSocketSession> sessions = WebSocketSessionsRegistry.getInstance().get(notification.getSessionKey());

        Set<ChangeStreamWebSocketSession> sessionsInError = Collections.newSetFromMap(new ConcurrentHashMap<>());

        sessions.stream().forEach(session -> {
            this.sendNotification(session, notification.getNotificationMessage(), sessionsInError);
        });

        sessionsInError.parallelStream().forEach(sessionInError -> {
            try {
                sessionInError.close();
            } catch (IOException e) {
                LOGGER.warn("error closing session in error {}", notification.getSessionKey());
            }
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
