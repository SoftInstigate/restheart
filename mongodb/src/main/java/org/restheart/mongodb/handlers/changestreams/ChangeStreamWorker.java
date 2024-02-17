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
import java.util.List;
import java.util.stream.Collectors;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.Document;
import org.restheart.mongodb.RHMongoClients;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;

/**
 * ChangeStreamWorker initiates and monitors the change changeStream and submit change event notifications
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChangeStreamWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeStreamWorker.class);

    private final ChangeStreamKey changeStreamKey;
    private final List<BsonDocument> resolvedStages;
    private final String dbName;
    private final String collName;

    public ChangeStreamWorker(ChangeStreamKey changeStreamKey, List<BsonDocument> resolvedStages, String dbName, String collName) {
        super();
        this.changeStreamKey = changeStreamKey;
        this.resolvedStages = resolvedStages;
        this.dbName = dbName;
        this.collName = collName;
    }


    @Override
    public void run() {
        var changeStream = starChangeStream();

        try {
            changeStream.forEach(notification -> onNext(notification));
        } catch(Throwable t) {
            closeAllWebSocketSessionsOnError(changeStreamKey);
        }
    }

    private void onNext(ChangeStreamDocument<?> notification) {
        if (!WebSocketSessions.getInstance().get(changeStreamKey).isEmpty()) {
            LOGGER.trace("[clients watching]: " + WebSocketSessions.getInstance().get(changeStreamKey).size());

            LOGGER.trace("change stream notification for changeStreamKey={}: {}", changeStreamKey, notification);

            Notifications.submit(new Notification(changeStreamKey, BsonUtils.toJson(getDocument(notification), changeStreamKey.getJsonMode())));
        } else {
            LOGGER.debug("closing change stream worker with no active websocket sessions, changeStreamKey=" + changeStreamKey);
            ChangeStreams.getInstance().remove(changeStreamKey);
            // exit the infinite changeStream.forEeach() loop and termminate the thread
            throw new IllegalStateException("terminate due to no active websocket sessions");
        }
    }

    private void closeAllWebSocketSessionsOnError(ChangeStreamKey cs) {
        var webSocketSessions = WebSocketSessions.getInstance();
        var websocketsToClose = webSocketSessions.get(cs);

        websocketsToClose.stream()
            .collect(Collectors.toSet())
            .forEach(wsk -> {
                try {
                    wsk.close();
                    webSocketSessions.remove(cs, wsk);
                } catch(IOException ioe) {
                    LOGGER.warn("Error closing websocket session {}", wsk, ioe);
                }
            });

        ChangeStreams.getInstance().remove(cs);
    }

    private ChangeStreamIterable<Document> starChangeStream() {
        try {
            return RHMongoClients.mclient()
                .getDatabase(dbName)
                .getCollection(collName)
                .watch(resolvedStages)
                .fullDocument(FullDocument.UPDATE_LOOKUP);
        }  catch(Throwable e) {
            LOGGER.warn("Error trying to start the stream: " + e.getMessage());
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
                LOGGER.warn("change stream fullDocument is not json {}", notification.getFullDocument());
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
