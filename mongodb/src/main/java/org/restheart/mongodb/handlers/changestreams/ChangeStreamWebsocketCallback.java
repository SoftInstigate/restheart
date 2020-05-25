/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
