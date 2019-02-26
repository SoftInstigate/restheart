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
package org.restheart.handlers.feed;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.bson.BsonDocument;
import org.bson.Document;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 *
 */
public class FeedWebsocketCallback implements WebSocketConnectionCallback {

    List<BsonDocument> pipeline;
    ScheduledFuture<?> scheduledThreadReference;
    ArrayList<WebSocketChannel> sessions = new ArrayList<WebSocketChannel>();

    public FeedWebsocketCallback(MongoCollection<BsonDocument> collection, List<BsonDocument> resolvedPipeline) {

        this.pipeline = resolvedPipeline;
        this.scheduledThreadReference
                = this.initScheduledPushNotificationsThread(
                        this.sessions,
                        collection.watch(this.pipeline)
                );

    }

    @Override

    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        channel.getReceiveSetter().set(new AbstractReceiveListener() {
        });
        channel.resumeReceives();
        this.setSessions(channel);

    }

    public void setSessions(WebSocketChannel sessions) {
        this.sessions.clear();
        this.sessions.add(sessions);

    }

    ScheduledFuture<?> initScheduledPushNotificationsThread(ArrayList<WebSocketChannel> sessions, ChangeStreamIterable changeStreamIterable) {
        class OneShotTask implements Runnable {

            ArrayList<WebSocketChannel> sessions;
            MongoCursor<ChangeStreamDocument<Document>> iterator;

            OneShotTask(ArrayList<WebSocketChannel> sessions, ChangeStreamIterable changeStreamIterable) {
                this.iterator = changeStreamIterable.iterator();
                this.sessions = sessions;
            }

            public void run() {
                pushNotifications(sessions, changeStreamIterable);
            }

            void pushNotifications(ArrayList<WebSocketChannel> sessions, ChangeStreamIterable changeStreamIterable) {
                ChangeStreamDocument<Document> changeStreamDocument = this.iterator.tryNext();
                if (changeStreamDocument != null) {

                    while (this.iterator.tryNext() != null) {}

                    if (!this.sessions.isEmpty()) {

                        for (WebSocketChannel session : this.sessions.get(0).getPeerConnections()) {
                            WebSockets.sendText(changeStreamDocument.toString(), session, null);
                        }
                    }
                }
            }

        }

        ScheduledExecutorService scheduler
                = Executors.newScheduledThreadPool(1);

        Thread t = new Thread(new OneShotTask(sessions, changeStreamIterable));

        return scheduler.scheduleAtFixedRate(t, 2, 2, SECONDS);

    }

    public void finalize() {
        this.scheduledThreadReference.cancel(true);
    }
}
