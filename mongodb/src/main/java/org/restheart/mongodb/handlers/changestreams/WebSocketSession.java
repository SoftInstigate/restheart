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

import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.websockets.core.WebSocketChannel;
/**
 *
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */

public class WebSocketSession {
    private final String sessionId;
    private final WebSocketChannel webSocketChannel;

    public WebSocketSession(WebSocketChannel channel) {
        this.sessionId = new SecureRandomSessionIdGenerator().createSessionId();
        this.webSocketChannel = channel;
        initChannelReceiveListener(webSocketChannel);
    }

    private void initChannelReceiveListener(WebSocketChannel channel) {
        channel.resumeReceives();
    }

    public void close() throws IOException {
        if (webSocketChannel != null) {
            webSocketChannel.close();
        }
    }

    public String getId() {
        return this.sessionId;
    }

    public WebSocketChannel getChannel() {
        return this.webSocketChannel;
    }
}
