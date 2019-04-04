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

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCollection;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.Optional;

import org.bson.BsonDocument;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestDispatcherHandler;
import org.restheart.handlers.metadata.InvalidMetadataException;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 *
 */
public class PutChangeStreamHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDispatcherHandler.class);

    public PutChangeStreamHandler() {
        super();
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public PutChangeStreamHandler(PipedHttpHandler next) {
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

        if (context.getChangeStreamIdentifier() == null) {
            LOGGER.debug(
                    "This method is not allowed: returning a <{}> HTTP code",
                    HttpStatus.SC_METHOD_NOT_ALLOWED);
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_METHOD_NOT_ALLOWED,
                    "method " + context.getMethod().name() + " not allowed");

            next(exchange, context);
        }

        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        List<BsonDocument> resolvedStages;

        try {
            resolvedStages = getResolvedStagesAsList(context);
        } catch (QueryNotFoundException ex) {
            ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_NOT_FOUND,
                    "query does not exist");
            next(exchange, context);
            return;
        }

        String changesStreamIdentifier =  context.getChangeStreamIdentifier();
        String changesStreamUriPath = getChangeStreamOperationUri(context) + "/" + changesStreamIdentifier;

        CacheManagerSingleton.cacheChangeStreamCursor(changesStreamUriPath,
                new CacheableChangeStreamCursor(initChangeStream(context, resolvedStages).iterator(), resolvedStages));

        ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_CREATED,
                "waiting for client ws at " + changesStreamUriPath);

        next(exchange, context);

    }

    private List<BsonDocument> getResolvedStagesAsList(RequestContext context) throws InvalidMetadataException, QueryVariableNotBoundException, Exception {

        String changesStreamOperation = context.getChangeStreamOperation();
        List<ChangeStreamOperation> streams = ChangeStreamOperation.getFromJson(context.getCollectionProps());
        Optional<ChangeStreamOperation> _query = streams.stream().filter(q -> q.getUri().equals(changesStreamOperation))
                .findFirst();

        if (!_query.isPresent()) {
            throw new QueryNotFoundException("Query does not exist");
        }

        ChangeStreamOperation pipeline = _query.get();

        List<BsonDocument> resolvedStages = pipeline.getResolvedStagesAsList(context.getAggreationVars());
        return resolvedStages;
    }

    private String getChangeStreamOperationUri(RequestContext context) {
        return "/" + context.getDBName() + "/" + context.getCollectionName() + "/_streams/"
                + context.getChangeStreamOperation();
    }


    private ChangeStreamIterable initChangeStream(RequestContext context, List<BsonDocument> resolvedStages) {

        MongoCollection<BsonDocument> collection = getDatabase().getCollection(context.getDBName(),
                context.getCollectionName());

        return collection.watch(resolvedStages);
    }
}
