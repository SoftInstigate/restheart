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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.bson.BsonDocument;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.restheart.db.MongoDBReactiveClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Options;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 *
 */
public class ChangeStreamWebsocketCallback implements WebSocketConnectionCallback {

    private static final String NOTIFICATION_MESSAGE = "Notification message from change stream source";

    private static boolean ALREADY_NOTIFYING = false;
    private static boolean CHECK_ONE_MORE_TIME = false;

    private static final Logger LOGGER
            = LoggerFactory.getLogger(ChangeStreamWebsocketCallback.class);

    public ChangeStreamWebsocketCallback() {

        MongoDBReactiveClientSingleton
                .getInstance()
                .getClient()
                .watch().subscribe(new Subscriber<ChangeStreamDocument>() {
                    @Override
                    public void onSubscribe(final Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(final ChangeStreamDocument changeStreamDocument) {
                        notifyClients();
                    }

                    @Override
                    public void onError(final Throwable t) {
                        System.out.println("Failed");
                    }

                    @Override
                    public void onComplete() {
                        System.out.println("Completed");
                    }
                });
    }

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {

        channel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {

                webSocketChannel.close();

                ChangeStreamCacheManagerSingleton.cleanUp();

                Collection<Optional<CacheableChangeStreamCursor>> streams
                        = ChangeStreamCacheManagerSingleton.getCachedChangeStreams();

                streams.stream().map((_stream) -> _stream.get())
                        .forEach((stream) -> {
                            clearClosedChannels(stream.getSessions());
                        });

            }
        });

        addChannelToCursorSessions(exchange, channel);

        channel.resumeReceives();
    }

    private void clearClosedChannels(Set<WebSocketChannel> channels) {
        
        Set<WebSocketChannel> toBeRemoved = new HashSet<>();

        channels.stream().filter((channel) -> (!channel.isOpen()))
                .forEachOrdered((channel) -> {
                    toBeRemoved.add(channel);
                });
        
        toBeRemoved.stream().forEach((channel) -> {
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

            ChangeStreamCacheManagerSingleton.cleanUp();
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
                    LOGGER.debug("Sending notifications to listening WebSockets; [clients]: " + stream.getSessions().toString());
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
