/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.restheart.handlers.stream;

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
import org.restheart.utils.JsonUtils;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author omartrasatti
 */
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
