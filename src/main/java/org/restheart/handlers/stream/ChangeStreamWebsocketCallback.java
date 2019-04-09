/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.stream;

import com.mongodb.MongoClient;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.bson.Document;
import org.restheart.db.MongoDBClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 *
 */
public class ChangeStreamWebsocketCallback implements WebSocketConnectionCallback {

    private static final String NOTIFICATION_MESSAGE = "Notification message from change stream source";

    private static boolean ALREADY_NOTIFYING = false;
    private static boolean CHECK_ONE_MORE_TIME = false;
    private static boolean FIRST_CLIENT_CONNECTED = false;

    private static final Logger LOGGER
            = LoggerFactory.getLogger(ChangeStreamWebsocketCallback.class);

    private static final Consumer<ChangeStreamDocument> CHECK_FOR_NOTIFICATIONS = (ChangeStreamDocument x) -> {
        notifyClients();
    };

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {

        channel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {

                webSocketChannel.close();

                Collection<Optional<CacheableChangeStreamCursor>> streams
                        = ChangeStreamCacheManagerSingleton.getCachedChangeStreams();

                streams.stream().map((_stream) -> _stream.get())
                        .forEachOrdered((stream) -> {
                            clearClosedChannels(stream.getSessions());
                        });

                clearStreams();
            }

            protected void onPong(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
                LOGGER.debug("PONG MESSAGE RECEIVED");
            }
        });

        addChannelToCursorSessions(exchange, channel);

        channel.resumeReceives();

        if (!FIRST_CLIENT_CONNECTED) {
            FIRST_CLIENT_CONNECTED = true;
            MongoClient client = MongoDBClientSingleton.getInstance().getClient();
            client.watch().forEach(CHECK_FOR_NOTIFICATIONS);
        }

    }

    private void clearStreams() {
        Collection<Optional<CacheableChangeStreamCursor>> streams
                = ChangeStreamCacheManagerSingleton.getCachedChangeStreams();

        streams.stream().map((_stream) -> _stream.get())
                .filter((stream) -> (!isAnyClientListeningForNotifications(stream)))
                .forEachOrdered((stream) -> {
                    clearStreamFromCache(stream);
                });
    }

    private void clearStreamFromCache(CacheableChangeStreamCursor stream) {
        Map<CacheableChangeStreamKey, Optional<CacheableChangeStreamCursor>> cacheMap
                = ChangeStreamCacheManagerSingleton.getCacheAsMap();

        cacheMap.entrySet().stream()
                .filter((entry) -> (entry.getValue().get().equals(stream)))
                .forEachOrdered((entry) -> {
            cacheMap.remove(entry.getKey());
        });
    }

    private boolean isAnyClientListeningForNotifications(CacheableChangeStreamCursor stream) {
        return stream.getSessions().size() > 0;
    }

    private void clearClosedChannels(Set<WebSocketChannel> channels) {

        channels.stream().filter((channel) -> (!channel.isOpen()))
                .forEachOrdered((channel) -> {
                    channels.remove(channel);
                });

    }

    private void addChannelToCursorSessions(WebSocketHttpExchange exchange, WebSocketChannel channel) {

        try {
            CacheableChangeStreamCursor cursor
                    = getRequestedCursor(exchange,
                            getChangeStreamPathFromChannelUrl(channel.getUrl()));

            if (cursor != null) {
                cursor.addSession(channel);
            } else {
                LOGGER.error("No cursor found on WebSocket Callback");
            }

        } catch (MalformedURLException ex) {
            LOGGER.error("Malformed URL: " + channel.getUrl());
        }

    }

    public static void notifyClients() {
        if (ALREADY_NOTIFYING == false) {

            ALREADY_NOTIFYING = true;

            checkCursorsForNotifications();

            ALREADY_NOTIFYING = false;

            if (CHECK_ONE_MORE_TIME == true) {
                CHECK_ONE_MORE_TIME = false;
                notifyClients();
            }

        } else {
            CHECK_ONE_MORE_TIME = true;
        }
    }

    private static boolean cursorHasNotification(CacheableChangeStreamCursor stream) {
        ChangeStreamDocument<Document> changeStreamDocument
                = stream.getIterator().tryNext();

        if (changeStreamDocument != null) {
            // Move ahead cursor over changes
            while (stream.getIterator().tryNext() != null) {
            }

            return true;
        }
        return false;
    }

    private static void checkCursorsForNotifications() {
        Collection<Optional<CacheableChangeStreamCursor>> streams
                = ChangeStreamCacheManagerSingleton.getCachedChangeStreams();

        streams.stream().map((_stream) -> _stream.get())
                .filter((stream) -> (cursorHasNotification(stream)))
                .forEachOrdered((stream) -> {
                    pushNotifications(stream.getSessions());
                });
    }

    private static void pushNotifications(Set<WebSocketChannel> channels) {
        channels.forEach((channel) -> {
            WebSockets.sendText(NOTIFICATION_MESSAGE, channel, null);
        });
    }

    private static String getChangeStreamPathFromChannelUrl(String webSocketUrl) throws MalformedURLException {
        return new URL("http" + webSocketUrl.substring(2)).getPath();
    }

    private CacheableChangeStreamCursor getRequestedCursor(WebSocketHttpExchange exchange, String requestPath) {

        List<BsonDocument> pipeline
                = exchange
                        .getAttachment(GetChangeStreamHandler.AVARS_ATTACHMENT_KEY);

        Set<CacheableChangeStreamKey> openedChangeStreams
                = ChangeStreamCacheManagerSingleton.getChangeStreamsKeySet();

        CacheableChangeStreamKey requestedStreamKey
                = new CacheableChangeStreamKey(requestPath, pipeline);

        // Find requested key into cache's keyset;
        for (CacheableChangeStreamKey key : openedChangeStreams) {

            if (key.getAVars().equals(requestedStreamKey.getAVars())
                    && key.getUrl().equals(requestedStreamKey.getUrl())) {
                requestedStreamKey = key;
                break;
            }
        }

        // Get requested cursor;
        CacheableChangeStreamCursor cachedCursor
                = ChangeStreamCacheManagerSingleton
                        .getCachedChangeStreamIterable(requestedStreamKey);

        return cachedCursor;
    }

}
