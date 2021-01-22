package org.restheart.mongodb.handlers.changestreams;

import java.io.IOException;
import java.util.stream.Collectors;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "obsoleteChangeStreamRemover", description = "removes obsolete change stream and web socket sessions (due to deleted db/collection, or updated change stream definition)", interceptPoint = InterceptPoint.RESPONSE)
public class ObsoleteChangeStreamRemover implements MongoInterceptor {
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if (request.isDelete() && request.isDb()) {
            closeAllOnDb(request.getDBName());
        } else if (request.isDelete() && request.isCollection()) {
            closeAllOnCollection(request.getDBName(), request.getCollectionName());
        } else if ((request.isPut() || request.isPatch()) && request.isCollection()) {
            // here we need to check if the collection stream definitions got updated
            var oldStreams = response.getDbOperationResult() != null
                ? response.getDbOperationResult().getOldData() != null
                ? response.getDbOperationResult().getOldData().containsKey("streams")
                ? response.getDbOperationResult().getOldData().get("streams")
                : null : null : null;

            var newStreams = response.getDbOperationResult() != null
                ? response.getDbOperationResult().getNewData() != null
                ? response.getDbOperationResult().getNewData().containsKey("streams")
                ? response.getDbOperationResult().getNewData().get("streams")
                : null : null : null;

            if ((oldStreams == null && newStreams != null)
            || (oldStreams != null && newStreams == null)
            || (oldStreams != null && newStreams != null
                    && !oldStreams.equals(newStreams))) { // <- NEED TO CHECK THIS EQUALS
                closeAllOnCollection(request.getDBName(), request.getCollectionName());
            }
        }
    }

    private void closeAllOnDb(String db) {
        var webSocketSessions = WebSocketSessionsRegistry.getInstance();
        var changeStreams = ChangeStreamsRegistry.getInstance();

        var sessionKeys = changeStreams.getSessionKeysOnDb(db);

        sessionKeys.stream()
            .collect(Collectors.toSet())
            .forEach(sk -> {
            var _webSocketSession = webSocketSessions.get(sk);
            _webSocketSession.stream()
                .collect(Collectors.toSet())
                .forEach(wss -> {
                    try {
                        wss.close();
                        webSocketSessions.remove(sk, wss);
                    } catch(IOException ioe) {
                        // LOGGER
                    }
            });

            changeStreams.remove(sk);
        });
    }

    private void closeAllOnCollection(String db, String collection) {
        var webSocketSessions = WebSocketSessionsRegistry.getInstance();
        var changeStreams = ChangeStreamsRegistry.getInstance();

        var sessionKeys = changeStreams.getSessionKeysOnCollection(db, collection);

        sessionKeys.stream()
            .collect(Collectors.toSet())
            .forEach(sk -> {
            var _webSocketSession = webSocketSessions.get(sk);
            _webSocketSession.stream()
                .collect(Collectors.toSet())
                .forEach(wss -> {
                    try {
                        wss.close();
                        webSocketSessions.remove(sk, wss);
                    } catch(IOException ioe) {
                        // LOGGER
                    }
            });

            changeStreams.remove(sk);
        });
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return (request.isDelete() && request.isCollection())
        || (request.isPut() && request.isCollection())
        || (request.isPatch() && request.isCollection())
        || (request.isDelete() && request.isDb());
    }
}
