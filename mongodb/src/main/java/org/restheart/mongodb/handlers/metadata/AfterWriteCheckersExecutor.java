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
package org.restheart.mongodb.handlers.metadata;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.exchange.RequestContext;
import org.restheart.mongodb.db.DAOUtils;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.mongodb.metadata.CheckerMetadata;
import org.restheart.plugins.mongodb.Checker;
import org.restheart.plugins.mongodb.GlobalChecker;
import org.restheart.utils.HttpStatus;

/**
 * Interceptor that executes after-write checkers
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AfterWriteCheckersExecutor extends BeforeWriteCheckersExecutor {

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange)
            throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);

        if ((doesCheckersApply(exchange)
                && !applyCheckers(exchange))
                || (doesGlobalCheckersApply()
                && !applyGlobalCheckers(exchange))) {
            // restore old data

            MongoClient client = MongoClientSingleton
                    .getInstance()
                    .getClient();

            MongoDatabase mdb = client
                    .getDatabase(request.getDBName());

            MongoCollection<BsonDocument> coll = mdb
                    .getCollection(
                            request.getCollectionName(),
                            BsonDocument.class);

            BsonDocument oldData = response
                    .getDbOperationResult()
                    .getOldData();

            Object newEtag = response.getDbOperationResult().getEtag();

            if (oldData != null) {
                // document was updated, restore old one
                DAOUtils.restoreDocument(
                        request.getClientSession(),
                        coll,
                        oldData.get("_id"),
                        request.getShardKey(),
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
                Object newId = response.getDbOperationResult()
                        .getNewData().get("_id");

                coll.deleteOne(and(eq("_id", newId), eq("_etag", newEtag)));
            }

            response.setIError(
                    HttpStatus.SC_BAD_REQUEST,
                    "request check failed");
        }
        
        next(exchange);
    }

    @Override
    boolean doesCheckersApply(HttpServerExchange exchange) {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);

        return request.getCollectionProps() != null
                && (((request.isPut()
                || request.isPatch())
                && request.isFile()
                || request.isDocument()
                || request.isSchema())
                || request.isPost()
                && (request.isCollection()
                || request.isFilesBucket()
                || request.isSchemaStore()))
                && request.getCollectionProps()
                        .containsKey(CheckerMetadata.ROOT_KEY)
                && (response.getDbOperationResult() != null
                && response.getDbOperationResult().getHttpCode() < 300);
    }

    /**
     *
     * @param context
     * @param checker
     * @return
     */
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
