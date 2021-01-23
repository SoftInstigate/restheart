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

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author omartrasatti
 */
@SuppressWarnings("rawtypes")
public class ChangeStreamSubscriber implements Subscriber<ChangeStreamDocument> {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(ChangeStreamSubscriber.class);

    private final SessionKey sessionKey;
    private Subscription sub;

    public ChangeStreamSubscriber(SessionKey sessionKey) {
        super();
        this.sessionKey = sessionKey;
    }

    @Override
    public void onSubscribe(final Subscription s) {
        s.request(Long.MAX_VALUE);
        this.sub = s;
    }

    @Override
    public void onNext(ChangeStreamDocument notification) {
        if (!GuavaHashMultimapSingleton.get(sessionKey).isEmpty()) {
            LOGGER.trace("[clients watching]: "
                    + GuavaHashMultimapSingleton.get(sessionKey).size());

            LOGGER.debug("Change stream notification for sessionKey={}: {}",
                    sessionKey,
                    notification);

            ChangeStreamWebsocketCallback.NOTIFICATION_PUBLISHER.submit(
                    new ChangeStreamNotification(sessionKey,
                            JsonUtils.toJson(
                                    getDocument(notification),
                                    sessionKey.getJsonMode())));
        } else {
            this.stop();
            LOGGER.debug("Closing unwatched stream, sessionKey=" + sessionKey);
            GetChangeStreamHandler.OPENED_STREAMS.remove(sessionKey);
        }
    }

    @Override
    public void onError(final Throwable t) {
        LOGGER.warn("Error from stream: " + t.getMessage());
    }

    @Override
    public void onComplete() {
        LOGGER.debug("Stream completed, sessionKey=" + sessionKey);
    }

    public void stop() {
        this.sub.cancel();
    }
    
    private BsonDocument getDocument(ChangeStreamDocument notification) {
        var doc = new BsonDocument();

        if (notification == null) {
            return doc;
        }
        
        doc.put("fullDocument", toBson((Document) notification.getFullDocument()));
        
        doc.put("documentKey", notification.getDocumentKey());

        if (notification.getUpdateDescription() != null) {
            var updateDescription = new BsonDocument();
            
            var updatedFields = notification.getUpdateDescription()
                    .getUpdatedFields();

            if (updatedFields != null) {
                updateDescription.put("updatedFields", updatedFields);
            } else {
                updateDescription.put("updatedFields", BsonNull.VALUE);
            }

            var removedFields = notification.getUpdateDescription()
                    .getRemovedFields();

            if (removedFields == null) {
                updateDescription.put("updatedFields", new BsonArray());
            } else {
                var _removedFields = new BsonArray();
                removedFields.forEach(rf -> _removedFields
                        .add(new BsonString(rf)));

                updateDescription.put("removedFields", _removedFields);
            }

            doc.put("updateDescription", updateDescription);
        } else {
            doc.put("updateDescription", BsonNull.VALUE);
        }

        doc.put("operationType", new BsonString(notification.getOperationType().getValue()));

        return doc;
    }
    
    private static final CodecRegistry REGISTRY = CodecRegistries
            .fromCodecs(new DocumentCodec());

    private static BsonValue toBson(Document document) {
        return document == null
                ? BsonNull.VALUE
                : document.toBsonDocument(BsonDocument.class, REGISTRY);
    }
}
