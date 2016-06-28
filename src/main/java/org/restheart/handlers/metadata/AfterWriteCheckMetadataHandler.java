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
import static com.mongodb.client.model.Filters.eq;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.util.List;
import org.restheart.db.DAOUtils;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.hal.metadata.RequestChecker;
import org.restheart.hal.metadata.singletons.Checker;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.bson.BsonDocument;
import static com.mongodb.client.model.Filters.and;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AfterWriteCheckMetadataHandler
        extends BeforeWriteCheckMetadataHandler {
    public AfterWriteCheckMetadataHandler() {
        super(null);
    }

    public AfterWriteCheckMetadataHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    protected boolean doesCheckerApply(
            RequestContext context,
            Checker checker) {
        return checker.getPhase(context) == Checker.PHASE.AFTER_WRITE;
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (doesCheckerAppy(context)) {
            if (!check(exchange, context)) {
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

                // send error response
                StringBuilder sb = new StringBuilder();
                sb.append("schema check failed");

                List<String> warnings = context.getWarnings();

                if (warnings != null && !warnings.isEmpty()) {
                    warnings.stream().forEach(w -> {
                        sb.append(", ").append(w);
                    });
                }

                BsonDocument oldData = context
                        .getDbOperationResult()
                        .getOldData();

                Object newEtag = context.getDbOperationResult().getEtag();

                if (oldData != null) {
                    // document was updated, restore old one
                    DAOUtils.restoreDocument(coll,
                            oldData.get("_id"),
                            context.getShardKey(),
                            oldData,
                            newEtag);

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
                        sb.toString());
            }
        }

        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }

    private boolean doesCheckerAppy(RequestContext context) {
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
                        .containsKey(RequestChecker.ROOT_KEY)
                && context.getDbOperationResult().getHttpCode() < 300;
    }
}
