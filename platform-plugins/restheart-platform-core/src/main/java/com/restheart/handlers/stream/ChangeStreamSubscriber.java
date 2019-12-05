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
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.json.JsonMode;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
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

    private static final CodecRegistry registry = CodecRegistries
            .fromCodecs(new DocumentCodec());

    private static BsonDocument toBson(Document document) {
        return document.toBsonDocument(BsonDocument.class, registry);
    }

    private final String streamKey;
    private JsonMode jsonMode = null;
    private Subscription sub;

    public ChangeStreamSubscriber(String streamKey) {
        super();
        this.streamKey = streamKey;
    }

    public ChangeStreamSubscriber(String streamKey, JsonMode jsonMode) {
        super();
        this.streamKey = streamKey;
        this.jsonMode = jsonMode;
    }

    @Override
    public void onSubscribe(final Subscription s) {
        s.request(Long.MAX_VALUE);
        this.sub = s;
    }

    @Override
    public void onNext(ChangeStreamDocument notification) {

        if (GuavaHashMultimapSingleton.getSessions(streamKey).size() > 0) {
            LOGGER.info("[clientsWatching]: "
                    + GuavaHashMultimapSingleton.getSessions(streamKey).size());

            String test = JsonUtils.toJson(toBson((Document) notification.getFullDocument()), this.jsonMode);
            ChangeStreamWebsocketCallback.NOTIFICATION_PUBLISHER
                    .submit(new ChangeStreamNotification(streamKey,
                            JsonUtils.toJson(toBson((Document) notification.getFullDocument()), this.jsonMode))
                    );
        } else {
            this.stop();
            LOGGER.info("Closing unwatched stream; [stream]: " + streamKey);
            GetChangeStreamHandler.OPENED_STREAMS.remove(this.streamKey);
        }

    }

    @Override
    public void onError(final Throwable t) {
        LOGGER.warn("Stopping reactive client from listening to changes; [errorMsg]: " + t.getMessage());
    }

    @Override
    public void onComplete() {
        LOGGER.warn("Stream completed;");
    }

    public void stop() {
        this.sub.cancel();
    }

}
