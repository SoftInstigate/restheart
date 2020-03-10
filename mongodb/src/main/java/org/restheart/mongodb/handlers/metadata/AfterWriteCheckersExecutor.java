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
package org.restheart.mongodb.handlers.metadata;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.mongodb.db.DAOUtils;
import org.restheart.mongodb.db.MongoDBClientSingleton;
import org.restheart.mongodb.metadata.CheckerMetadata;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.plugins.Checker;
import org.restheart.plugins.GlobalChecker;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "afterWriteCheckerExecutor",
        description = "executes before write checkers",
        interceptPoint = InterceptPoint.RESPONSE)
public class AfterWriteCheckersExecutor
        extends BeforeWriteCheckersExecutor implements Service {

    /**
     *
     * @param pluginsRegistry
     */
    @InjectPluginsRegistry
    public AfterWriteCheckersExecutor(PluginsRegistry pluginsRegistry) {
        super(pluginsRegistry);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handle(HttpServerExchange exchange)
            throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        
        if ((doesCheckersApply(exchange)
                && !applyCheckers(exchange))
                || (doesGlobalCheckersApply()
                && !applyGlobalCheckers(exchange))) {
            // restore old data

            MongoClient client = MongoDBClientSingleton
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

            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_BAD_REQUEST,
                    "request check failed");
        }
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
