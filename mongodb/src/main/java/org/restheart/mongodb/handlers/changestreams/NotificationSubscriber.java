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

import java.io.IOException;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

/**
 *
 * @author omartrasatti
 */
public class NotificationSubscriber implements Subscriber<Notification> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSubscriber.class);

    private Subscription sub;

    @Override
    public void onSubscribe(final Subscription s) {
        s.request(Long.MAX_VALUE);
        this.sub = s;
    }

    @Override
    public void onNext(Notification notification) {
        var sessions = WebSocketSessions.getInstance().get(notification.getKey());

        var msg = notification.getMessage();

        sessions.stream().forEach(session -> ThreadsUtils.virtualThreadsExecutor().execute(() -> {
            LOGGER.debug("sending change event to websocket session {}", session.getId());
            this.send(session, msg);
        }));
    }

    @Override
    public void onError(final Throwable t) {
        LOGGER.warn("Error sending websocket message", t.getMessage());
    }

    @Override
    public void onComplete() {
        LOGGER.warn("Notification subscription completed. This should never happen!");
    }

    public void stop() {
        LOGGER.warn("Notification subscription stopped. This should never happen!");
        this.sub.cancel();
    }

    private void send(WebSocketSession session, String message) {
        WebSockets.sendText(message, session.getChannel(), new WebSocketCallback<Void>() {
            @Override
            public void complete(final WebSocketChannel channel, Void context) {
            }

            @Override
            public void onError(final WebSocketChannel channel, Void context, Throwable throwable) {
                try {
                    session.close();
                    LOGGER.info("websocket session closed! {}", session.getId());
                } catch (IOException e) {
                    LOGGER.warn("Error closing session in error {}", session.getId());
                }
            }
        });
    }
}
