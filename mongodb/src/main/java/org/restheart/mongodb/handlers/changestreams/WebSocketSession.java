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
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.restheart.mongodb.MongoServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.XnioExecutor;

import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

/**
 *
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */

public class WebSocketSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketSession.class);

    private final String id;
    private final WebSocketChannel channel;
    private final ChangeStreamWorker changeStreamWorker;
    private final Map<String, String> boundVars;
    private final XnioExecutor.Key keepAlive;

    public WebSocketSession(WebSocketChannel channel, ChangeStreamWorker csw, Map<String, String> boundVars) {
        this.id = new SecureRandomSessionIdGenerator().createSessionId();
        this.channel = channel;
        this.channel.getReceiveSetter().set(new ClientFramesListener());
        this.channel.resumeReceives(); // required to get close messages and pings from client
        this.changeStreamWorker = csw;
        this.boundVars = boundVars != null ? Map.copyOf(boundVars) : Map.of();
        this.keepAlive = scheduleKeepAlive(channel);

        this.channel.addCloseTask((WebSocketChannel channel1) -> {
            if (this.keepAlive != null) {
                this.keepAlive.remove();
            }

            this.changeStreamWorker.websocketSessions().removeIf(s -> s.getId().equals(id));

            if (this.changeStreamWorker.websocketSessions().isEmpty()
                    && this.changeStreamWorker.sseSessions().isEmpty()) {
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

    /**
     * Pings the client at the change streams keep-alive interval, as the SSE transport sends a
     * comment. A stream with nothing to report sends nothing at all, and a load balancer or proxy
     * in front of RESTHeart drops a connection idle for its timeout without closing it: the client
     * keeps waiting on a socket that will never deliver another event. The ping keeps the
     * connection in use, and a ping that cannot be written closes the session, so the worker stops
     * serving a client that is gone. Browsers answer pings on their own.
     *
     * @return the scheduled task, or null when the keep-alive is disabled
     */
    private static XnioExecutor.Key scheduleKeepAlive(WebSocketChannel channel) {
        var keepAliveMs = MongoServiceConfiguration.get().getChangeStreamsKeepAliveMs();
        if (keepAliveMs <= 0) {
            return null;
        }

        return channel.getIoThread().executeAtInterval(() -> {
            if (channel.isOpen()) {
                WebSockets.sendPing(ByteBuffer.allocate(0), channel, null);
            }
        }, keepAliveMs, TimeUnit.MILLISECONDS);
    }

    public void close() throws IOException {
        if (channel != null) {
            try (channel) {
                channel.sendClose();
            } catch (IOException ioe) {
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

    public Map<String, String> getBoundVars() {
        return boundVars;
    }

    /**
     * What the client sends on a change stream: a ping is answered with a pong and a close
     * message closes the session, as {@link AbstractReceiveListener} does by default, so a client
     * can tell a live connection from one dropped in silence, e.g. after its computer slept.
     * Text and binary messages carry nothing for a change stream: the default handling reads and
     * drops them, here within a small buffer, so a large message cannot hold memory.
     */
    private static class ClientFramesListener extends AbstractReceiveListener {
        private static final long MAX_IGNORED_MESSAGE_BYTES = 4 * 1024;

        @Override
        protected long getMaxTextBufferSize() {
            return MAX_IGNORED_MESSAGE_BYTES;
        }

        @Override
        protected long getMaxBinaryBufferSize() {
            return MAX_IGNORED_MESSAGE_BYTES;
        }
    }
}
