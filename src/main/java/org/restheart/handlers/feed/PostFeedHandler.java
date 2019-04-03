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
import com.mongodb.client.model.changestream.ChangeStreamDocument;

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.bson.BsonDocument;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 *
 */
public class PostFeedHandler extends PipedHttpHandler {

        private static SecureRandom RND_GENERATOR = new SecureRandom();
        private static Consumer<ChangeStreamDocument> TRIGGER_NOTIFICATIONS = 
        (notification) -> {
                FeedWebsocketCallback.notifyClients();
        };


        private static Map<String, ChangeStreamIterable> CHANGE_STREAM_MAP = new HashMap();
        public PostFeedHandler() {
                super();
        }

        /**
         * Default ctor
         *
         * @param next
         */
        public PostFeedHandler(PipedHttpHandler next) {
                super(next);
        }

        /**
         *
         * @param exchange
         * @param context
         * @throws Exception
         */
        @Override
        public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {

                if (context.isInError()) {
                        next(exchange, context);
                        return;
                }

                String changesStreamOperation = context.getFeedOperation();
                List<FeedOperation> feeds = FeedOperation.getFromJson(context.getCollectionProps());
                Optional<FeedOperation> _query = feeds.stream().filter(q -> q.getUri().equals(changesStreamOperation))
                                .findFirst();

                if (!_query.isPresent()) {
                        ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_NOT_FOUND,
                                        "query does not exist");
                        next(exchange, context);
                        return;
                }

                FeedOperation pipeline = _query.get();

                String changesStreamIdentifier = getChangeStreamIdentifier(context);
                String changesStreamUriPath = getChangeStreamOperationUri(context) + "/" + changesStreamIdentifier;
                List<BsonDocument> resolvedStages = pipeline.getResolvedStagesAsList(context.getAggreationVars());
                ChangeStreamIterable changeStreamIterable = initChangeStream(context, resolvedStages);

                
                CacheManagerSingleton.cacheChangeStreamCursor(changesStreamUriPath, 
                new CacheableChangesStreamCursor(changeStreamIterable.iterator(), resolvedStages));
                
                if (CHANGE_STREAM_MAP.keySet().contains(getChangeStreamOperationUri(context) + "/" + changesStreamIdentifier)) {
                        CHANGE_STREAM_MAP.remove(getChangeStreamOperationUri(context) + "/" + changesStreamIdentifier);
                }
                
                CHANGE_STREAM_MAP.put(getChangeStreamOperationUri(context) + "/" + changesStreamIdentifier, changeStreamIterable);

                ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_CREATED,
                                "waiting for client ws at " + changesStreamUriPath);

                next(exchange, context);
                
                CHANGE_STREAM_MAP.get(getChangeStreamOperationUri(context) + "/" + changesStreamIdentifier)
                        .forEach(TRIGGER_NOTIFICATIONS);

        }

        private String generateChangeStreamRandomIdentifier() {
                String randomId = new BigInteger(256, RND_GENERATOR).toString(Character.MAX_RADIX).substring(0, 16);

                return randomId;
        }

        private String getChangeStreamOperationUri(RequestContext context) {
                return "/" + context.getDBName() + "/" + context.getCollectionName() + "/_feeds/"
                                + context.getFeedOperation();
        }

        private String getChangeStreamIdentifier(RequestContext context) {

                String identifier = context.getFeedIdentifier();
                if (identifier == null) {
                        identifier = generateChangeStreamRandomIdentifier();
                }

                return identifier;
        }

        private ChangeStreamIterable initChangeStream(RequestContext context, List<BsonDocument> resolvedStages ) {

                MongoCollection<BsonDocument> collection = getDatabase().getCollection(context.getDBName(),
                                context.getCollectionName());

                return collection.watch(resolvedStages);
        }
}
