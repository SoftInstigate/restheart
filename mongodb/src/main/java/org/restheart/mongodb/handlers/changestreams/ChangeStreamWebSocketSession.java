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

import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 *
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */

public class ChangeStreamWebSocketSession {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(ChangeStreamWebSocketSession.class);

    private final String sessionId;
    private final SessionKey sessionKey;
    private final WebSocketChannel webSocketChannel;

    public ChangeStreamWebSocketSession(WebSocketChannel channel, SessionKey sessionKey) {
        this.sessionId = new SecureRandomSessionIdGenerator().createSessionId();
        this.webSocketChannel = channel;
        this.sessionKey = sessionKey;
        initChannelReceiveListener(webSocketChannel);
    }

    private void initChannelReceiveListener(WebSocketChannel channel) {
        channel.getReceiveSetter().set(new ChangeStreamReceiveListener(this));

        channel.resumeReceives();
    }

    public String getId() {
        return this.sessionId;
    }

    public SessionKey getSessionKey() {
        return this.sessionKey;
    }

    public WebSocketChannel getChannel() {
        return this.webSocketChannel;
    }

    public void close() throws IOException {
        WebSocketSessionsRegistry.getInstance().remove(this.sessionKey, this);
        this.webSocketChannel.close();
    }

    class ChangeStreamReceiveListener extends AbstractReceiveListener {
        private final ChangeStreamWebSocketSession session;

        public ChangeStreamReceiveListener(ChangeStreamWebSocketSession session) {
            this.session = session;
        }
        @Override
        protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
            LOGGER.debug("Stream connection closed, sessionkey={}", sessionKey);
            this.session.close();
        }
    }
}
