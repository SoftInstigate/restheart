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
package org.restheart.handlers.metadata;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.restheart.db.DAOUtils;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.plugins.Checker;
import org.restheart.plugins.GlobalChecker;
import org.restheart.metadata.CheckerMetadata;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AfterWriteCheckHandler
        extends BeforeWriteCheckHandler {

    public AfterWriteCheckHandler() {
        super(null);
    }

    public AfterWriteCheckHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if ((doesCheckersApply(context)
                && !applyCheckers(exchange, context))
                || (doesGlobalCheckersApply()
                && !applyGlobalCheckers(exchange, context))) {
            // restore old data

            MongoClient client = MongoDBClientSingleton
                    .getInstance()
                    .getClient();

            MongoDatabase mdb = client
                    .getDatabase(context.getDBName());

            MongoCollection<BsonDocument> coll = mdb
                    .getCollection(
                            context.getCollectionName(),
                            BsonDocument.class);

            BsonDocument oldData = context
                    .getDbOperationResult()
                    .getOldData();

            Object newEtag = context.getDbOperationResult().getEtag();

            if (oldData != null) {
                // document was updated, restore old one
                DAOUtils.restoreDocument(
                        context.getClientSession(),
                        coll,
                        oldData.get("_id"),
                        context.getShardKey(),
                        oldData,
                        newEtag,
                        "_etag");

                // add to response old etag
                if (oldData.get("$set") != null
                        && oldData.get("$set").isDocument()
                        && oldData.get("$set")
                                .asDocument()
                                .get("_etag") != null) {
                    exchange.getResponseHeaders().put(Headers.ETAG,
                            oldData.get("$set")
                                    .asDocument()
                                    .get("_etag")
                                    .asObjectId()
                                    .getValue()
                                    .toString());
                } else {
                    exchange.getResponseHeaders().remove(Headers.ETAG);
                }

            } else {
                // document was created, delete it
                Object newId = context.getDbOperationResult()
                        .getNewData().get("_id");

                coll.deleteOne(and(eq("_id", newId), eq("_etag", newEtag)));
            }

            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "request check failed");
            next(exchange, context);
            return;
        }

        next(exchange, context);
    }

    @Override
    boolean doesCheckersApply(RequestContext context) {
        return context.getCollectionProps() != null
                && (((context.getMethod() == METHOD.PUT
                || context.getMethod() == METHOD.PATCH)
                && context.getType() == RequestContext.TYPE.FILE
                || context.getType() == RequestContext.TYPE.DOCUMENT
                || context.getType() == RequestContext.TYPE.SCHEMA)
                || context.getMethod() == METHOD.POST
                && (context.getType() == RequestContext.TYPE.COLLECTION
                || context.getType() == RequestContext.TYPE.FILES_BUCKET
                || context.getType() == RequestContext.TYPE.SCHEMA_STORE))
                && context.getCollectionProps()
                        .containsKey(CheckerMetadata.ROOT_KEY)
                && (context.getDbOperationResult() != null
                && context.getDbOperationResult().getHttpCode() < 300);
    }

    @Override
    protected boolean doesCheckersApply(
            RequestContext context,
            Checker checker) {
        return !context.isInError()
                && checker.getPhase(context) == Checker.PHASE.AFTER_WRITE;
    }

    @Override
    boolean doesGlobalCheckerApply(GlobalChecker gc,
            HttpServerExchange exchange,
            RequestContext context) {
        return gc.getPhase(context) == Checker.PHASE.AFTER_WRITE
                && gc.resolve(exchange, context);
    }
}
