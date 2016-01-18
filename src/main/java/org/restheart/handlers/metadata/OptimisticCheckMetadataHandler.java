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
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.util.List;
import org.bson.Document;
import org.restheart.db.DAOUtils;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.hal.metadata.RequestChecker;
import org.restheart.hal.metadata.singletons.Checker;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class OptimisticCheckMetadataHandler extends CheckMetadataHandler {
    public OptimisticCheckMetadataHandler() {
        super(null);
    }

    public OptimisticCheckMetadataHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    protected boolean doesCheckerApply(Checker checker) {
        return checker.getType() == Checker.TYPE.AFTER_WRITE;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (doesCheckerAppy(context)) {
            if (!check(exchange, context)) {
                // restore old data

                MongoClient client = MongoDBClientSingleton.getInstance().getClient();
                MongoDatabase mdb = client.getDatabase(context.getDBName());
                MongoCollection coll = mdb.getCollection(context.getCollectionName());

                Document data = context.getDbOperationResult().getOldData();

                DAOUtils.updateDocument(coll, data.get("_id"), data, true);

                // add to response old etag
                if (data.get("$set") != null
                        && data.get("$set") instanceof Document
                        && ((Document) data.get("$set")).get("_etag") != null) {
                    exchange.getResponseHeaders().put(Headers.ETAG,
                            ((Document) data.get("$set")).get("_etag")
                            .toString());
                } else {
                    exchange.getResponseHeaders().remove(Headers.ETAG);
                }

                // send error response
                StringBuilder sb = new StringBuilder();
                sb.append("schema check failed");

                List<String> warnings = context.getWarnings();

                if (warnings != null && !warnings.isEmpty()) {
                    warnings.stream().forEach(w -> {
                        sb.append(", ").append(w);
                    });
                }

                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, sb.toString());
            }
        }

        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }

    private boolean doesCheckerAppy(RequestContext context) {
        return context.getCollectionProps() != null
                && (context.getType() == RequestContext.TYPE.FILE
                || context.getType() == RequestContext.TYPE.DOCUMENT
                || context.getType() == RequestContext.TYPE.SCHEMA)
                && context.getCollectionProps().containsField(RequestChecker.ROOT_KEY)
                && context.getDbOperationResult().getHttpCode() < 300;
    }
}
