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
package org.restheart.handlers.collection;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PatchCollectionHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of PatchCollectionHandler
     */
    public PatchCollectionHandler() {
        super();
    }

    /**
     * Creates a new instance of PatchCollectionHandler
     *
     * @param next
     */
    public PatchCollectionHandler(PipedHttpHandler next) {
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

        if (context.getDBName().isEmpty()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "wrong request, db name cannot be empty");
            next(exchange, context);
            return;
        }

        if (context.getCollectionName().isEmpty()
                || (context.getCollectionName().startsWith("_")
                && !context.getCollectionName().equals(RequestContext._SCHEMAS))) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "wrong request, collection name cannot be "
                    + "empty or start with _");
            next(exchange, context);
            return;
        }

        BsonValue _content = context.getContent();

        if (_content == null) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            next(exchange, context);
            return;
        }

        // cannot PATCH with an array
        if (!_content.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data must be a json object");
            next(exchange, context);
            return;
        }

        if (_content.asDocument().isEmpty()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            next(exchange, context);
            return;
        }

        final BsonDocument content = _content.asDocument();

        if (isInvalidMetadata(content, exchange, context)) {
            return;
        }

        OperationResult result = getDatabase()
                .upsertCollection(
                        context.getClientSession(),
                        context.getDBName(),
                        context.getCollectionName(),
                        content,
                        context.getETag(),
                        true,
                        true,
                        context.isETagCheckRequired());

        if (isResponseInConflict(context, result, exchange)) {
            return;
        }

        context.setResponseStatusCode(result.getHttpCode());

        LocalCachesSingleton.getInstance()
                .invalidateCollection(
                        context.getDBName(),
                        context.getCollectionName());

        next(exchange, context);
    }

}
