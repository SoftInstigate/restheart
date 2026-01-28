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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.Document;
import org.restheart.mongodb.RHMongoClients;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoException;
import com.mongodb.MongoInterruptedException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;

import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

/**
 * ChangeStreamWorker initiates and monitors the MongoDB change stream
 * and dispaches virtual threads to send change event to clients
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChangeStreamWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeStreamWorker.class);

    private final ChangeStreamWorkerKey key;
    private final List<BsonDocument> resolvedStages;
    private final String dbName;
    private final String collName;
    private final Set<WebSocketSession> websocketSessions = Collections.synchronizedSet(new HashSet<>());

    private Thread handlingVirtualThread = null;

    public ChangeStreamWorker(ChangeStreamWorkerKey key, List<BsonDocument> resolvedStages, String dbName, String collName) {
        super();
        this.key = key;
        this.resolvedStages = resolvedStages;
        this.dbName = dbName;
        this.collName = collName;
    }

    public ChangeStreamWorkerKey getKey() {
        return this.key;
    }

    public String getDbName() {
        return this.dbName;
    }

    public String getCollName() {
        return this.collName;
    }

    public Thread handlingVirtualThread() {
        return this.handlingVirtualThread;
    }

    @Override
    public void run() {
        if (Thread.currentThread().isVirtual()) {
            this.handlingVirtualThread = Thread.currentThread();
        }

        LOGGER.debug("Change stream worker started {}", Thread.currentThread().getName());

        try {
            changeStreamEventsLoop();
        } catch(Throwable t) {
            if (t instanceof NoMoreWebSocketException) {
                LOGGER.debug("Closing Change Stream Worker {} since it has no active WebSocket sessions", key);
            } else {
                LOGGER.error("Change Stream Worker {} died due to exception", key, t);
            }

            closeAllWebSocketSessions();
        } finally {
            ChangeStreamWorkers.getInstance().remove(key);
            LOGGER.debug("Change stream worker ended");
        }
    }

    /**
     * executes the change stream events loop
     *
     * on MongoDB exceptions it reconnects to the change stream after 1 sec
     *
     **/
    private void changeStreamEventsLoop() {
        try {
            _changeStreamEventsLoop();
        } catch(MongoInterruptedException mie) {
            close();
        } catch(MongoException mqe) {
            LOGGER.error("MongoDB error on ChangeStreamWorker {}, restarting a new worker", key, mqe);

            try {
                Thread.sleep(1_000);
            } catch (InterruptedException ex) {
                // nothing to do
            } finally {
                changeStreamEventsLoop();
            }
        }
    }

    private void _changeStreamEventsLoop() {
        LOGGER.debug("Change Stream Worker {} started listening for change events", this.key);
        final var changeStream = startChangeStream();

        changeStream.forEach(changeEvent -> {
            if (this.websocketSessions.isEmpty()) {
                // this terminates the ChangeStreamWorker
                LambdaUtils.throwsSneakyException(new NoMoreWebSocketException());
            }

            var msg = BsonUtils.toJson(getDocument(changeEvent), key.getJsonMode());

            this.websocketSessions.stream().forEach(session -> ThreadsUtils.virtualThreadsExecutor().execute(() -> {
                try {
                    this.send(session, msg);
                    LOGGER.trace("Change event sent to WebSocket session {}", session.getId());
                } catch (Throwable t) {
                    LOGGER.error("Error sending change event to WebSocket session {}", session.getId(), t);
                }
            }));
        });
    }

    public Set<WebSocketSession> websocketSessions() {
        return this.websocketSessions;
    }

    private void send(WebSocketSession session, String message) {
        WebSockets.sendText(message, session.getChannel(), new WebSocketCallback<Void>() {
            @Override
            public void complete(final WebSocketChannel channel, Void context) {
            }

            @Override
            public void onError(final WebSocketChannel channel, Void context, Throwable throwable) {
                // close WebSocket session
                try {
                    session.close();
                    var sid = session.getId();
                    websocketSessions().removeIf(s -> s.getId().equals(sid));
                } catch (IOException e) {
                    LOGGER.warn("Error closing WebSocket session {}", session.getId(), e);
                }
            }
        });
    }

    /**
     * removes the workers form the list of active workers and
     * close all its websocket sessions and interrupt the handling virtual thread
     *
     * on next change event, the thread will terminate since it has no active websocket sesssions
     */
    void close() {
        ChangeStreamWorkers.getInstance().remove(key);
        closeAllWebSocketSessions();

        if (this.handlingVirtualThread != null && !this.handlingVirtualThread.isInterrupted()) {
            this.handlingVirtualThread.interrupt();
        }
    }

    void closeAllWebSocketSessions() {
	  new HashSet<>(websocketSessions)
            .forEach(wsk -> {
                try {
                    wsk.close();
                    websocketSessions.remove(wsk);
                } catch(IOException ioe) {
                    LOGGER.warn("Error closing WebSocket session {}", wsk, ioe);
                }
            });
    }

    private static class NoMoreWebSocketException extends Exception {}

    private ChangeStreamIterable<Document> startChangeStream() {
        try {
            return RHMongoClients.mclient()
                .getDatabase(dbName)
                .getCollection(collName)
                .watch(resolvedStages)
                .fullDocument(FullDocument.UPDATE_LOOKUP);
        }  catch(Throwable e) {
            LOGGER.warn("Error trying to start the stream: {}", e.getMessage(), e);
            throw e;
        }
    }

    private BsonDocument getDocument(ChangeStreamDocument<?> notification) {
        var doc = new BsonDocument();

        if (notification == null) {
            return doc;
        }

        if (notification.getFullDocument() != null) {
            try {
                doc.put("fullDocument", BsonUtils.documentToBson((Document) notification.getFullDocument()));
            } catch(ClassCastException cce) {
                LOGGER.warn("change event fullDocument is not json {}", notification.getFullDocument());
                doc.put("fullDocument", BsonNull.VALUE);
            }
        }

        if (notification.getDocumentKey() != null) {
            doc.put("documentKey", notification.getDocumentKey());
        }

        if (notification.getUpdateDescription() != null) {
            var updateDescription = new BsonDocument();

            var updatedFields = notification.getUpdateDescription().getUpdatedFields();

            if (updatedFields != null) {
                updateDescription.put("updatedFields", updatedFields);
            } else {
                updateDescription.put("updatedFields", BsonNull.VALUE);
            }

            var removedFields = notification.getUpdateDescription().getRemovedFields();

            if (removedFields == null) {
                updateDescription.put("updatedFields", new BsonArray());
            } else {
                var _removedFields = new BsonArray();
                removedFields.forEach(rf -> _removedFields.add(new BsonString(rf)));

                updateDescription.put("removedFields", _removedFields);
            }

            doc.put("updateDescription", updateDescription);
        }

        if (notification.getOperationType() != null) {
            doc.put("operationType", new BsonString(notification.getOperationType().getValue()));
        }

        return doc;
    }
}
