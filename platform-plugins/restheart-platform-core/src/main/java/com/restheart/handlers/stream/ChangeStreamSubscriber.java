/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.restheart.handlers.stream;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
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

    private final String streamKey;
    private Subscription sub;

    public ChangeStreamSubscriber(String streamKey) {
        super();
        this.streamKey = streamKey;
    }

    @Override
    public void onSubscribe(final Subscription s) {
        s.request(Long.MAX_VALUE);
        this.sub = s;
    }

    @Override
    public void onNext(ChangeStreamDocument notification) {
        if (GuavaHashMultimapSingleton.getSessions(streamKey).size() > 0) {
            LOGGER.info("[clientsWatching]: " + GuavaHashMultimapSingleton.getSessions(streamKey).size());
            ChangeStreamWebsocketCallback.NOTIFICATION_PUBLISHER
                    .submit(new ChangeStreamNotification(streamKey, notification.toString()));
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
