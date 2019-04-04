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
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.bson.Document;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 *
 */
public class ChangeStreamWebsocketCallback implements WebSocketConnectionCallback {

    private static Set<WebSocketChannel> PEER_CONNECTIONS = null;
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
        });
        PEER_CONNECTIONS = channel.getPeerConnections();
        channel.resumeReceives();

        if (!FIRST_CLIENT_CONNECTED) {
            FIRST_CLIENT_CONNECTED = true;
            MongoClient client = MongoDBClientSingleton.getInstance().getClient();
            client.watch().forEach(CHECK_FOR_NOTIFICATIONS);
        }

    }

    public static void notifyClients() {
        if (ALREADY_NOTIFYING == false) {
            ALREADY_NOTIFYING = true;
            checkCachedCursorsForNotifications();
            ALREADY_NOTIFYING = false;
            if (CHECK_ONE_MORE_TIME == true) {
                CHECK_ONE_MORE_TIME = false;
                notifyClients();
            }
        } else {
            CHECK_ONE_MORE_TIME = true;
        }
    }

    private static void checkCachedCursorsForNotifications() {
        Map<String, Optional<CacheableChangeStreamCursor>> cacheMap = CacheManagerSingleton.getCacheAsMap();

        for (Map.Entry<String, Optional<CacheableChangeStreamCursor>> cacheableChangeStreamEntry : cacheMap
                .entrySet()) {
            LOGGER.info("Checking notification for "+ cacheableChangeStreamEntry.getKey());
            MongoCursor<ChangeStreamDocument<Document>> iterator = cacheableChangeStreamEntry.getValue().get()
                    .getIterator();

            ChangeStreamDocument<Document> changeStreamDocument = iterator.tryNext();

            if (changeStreamDocument != null) {
                LOGGER.info("Notification found for "+ cacheableChangeStreamEntry.getKey());
                // Move ahead cursor over changes
                while (iterator.tryNext() != null) {
                }

                pushNotifications(cacheableChangeStreamEntry.getKey(), changeStreamDocument);
            }
        }

    }

    private static void pushNotifications(String streamUrl, ChangeStreamDocument<Document> data) {
        if (PEER_CONNECTIONS != null) {
            LOGGER.info("Pushing notifications");
            for (WebSocketChannel channel : PEER_CONNECTIONS) {

                try {
                    if (getHttpUrl(channel.getUrl()).equals(streamUrl)) {
                        WebSockets.sendText(data.toString(), channel, null);
                    }
                } catch (MalformedURLException ex) {
                    LOGGER.warn("URL parsing exception for " + channel.getUrl());
                }

            }
        }
    }

    private static String getHttpUrl(String webSocketUrl) throws MalformedURLException {
        String result = webSocketUrl.substring(2); // remove ws protocol part of URL
        result = "http" + result;
        result = new URL(result).getPath();
        return result;
    }

}
