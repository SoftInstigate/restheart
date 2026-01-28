/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.websockets.core.WebSocketChannel;
/**
 *
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */

public class WebSocketSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketSession.class);

    private final String id;
    private final WebSocketChannel channel;
    private final ChangeStreamWorker changeStreamWorker;

    public WebSocketSession(WebSocketChannel channel, ChangeStreamWorker csw) {
        this.id = new SecureRandomSessionIdGenerator().createSessionId();
        this.channel = channel;
        this.channel.resumeReceives(); // required to get close messages from client
        this.changeStreamWorker = csw;

        this.channel.addCloseTask((WebSocketChannel channel1) -> {
            this.changeStreamWorker.websocketSessions().removeIf(s -> s.getId().equals(id));

            if (this.changeStreamWorker.websocketSessions().isEmpty()) {
                if (this.changeStreamWorker.handlingVirtualThread() != null) {
                    LOGGER.debug("Terminating worker {}", this.changeStreamWorker.handlingVirtualThread().getName());
                    this.changeStreamWorker.handlingVirtualThread().interrupt();
                } else {
                    LOGGER.warn("Cannot terminate worker since handlingVirtualThread is null");
                }
            }

            try {
                this.close();
            } catch (IOException ex) {
                // nothing to do
            }
        });
    }

    // private void initChannelReceiveListener(WebSocketChannel channel) {
    //     channel.resumeReceives();
    // }

    public void close() throws IOException {
        if (channel != null) {
            try (channel) {
                channel.sendClose();
            } catch(IOException ioe) {
                // nothing to do
            }
        }

        LOGGER.debug("WebSocket session closed {}", getId());
    }

    public String getId() {
        return this.id;
    }

    public WebSocketChannel getChannel() {
        return this.channel;
    }
}
