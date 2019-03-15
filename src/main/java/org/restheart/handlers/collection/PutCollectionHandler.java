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
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.Database;
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
public class PutCollectionHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of PutCollectionHandler
     */
    public PutCollectionHandler() {
        super();
    }

    /**
     * Creates a new instance of PutCollectionHandler
     *
     * @param next
     */
    public PutCollectionHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of PutCollectionHandler
     *
     * @param next
     * @param dbsDAO
     */
    public PutCollectionHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context) throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        BsonValue _content = context.getContent();

        if (_content == null) {
            _content = new BsonDocument();
        }

        // cannot PUT an array
        if (!_content.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data must be a json object");
            next(exchange, context);
            return;
        }

        final BsonDocument content = _content.asDocument();

        if (isInvalidMetadata(content, exchange, context)) {
            return;
        }

        boolean updating = context.getCollectionProps() != null;

        OperationResult result = getDatabase().upsertCollection(
                context.getClientSession(),
                context.getDBName(),
                context.getCollectionName(),
                content,
                context.getETag(),
                updating, false,
                context.isETagCheckRequired());

        context.setDbOperationResult(result);

        // invalidate the cache collection item
        LocalCachesSingleton.getInstance().invalidateCollection(
                context.getDBName(),
                context.getCollectionName());

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_CONFLICT,
                    "The collection's ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header.");
            next(exchange, context);
            return;
        }

        context.setResponseStatusCode(result.getHttpCode());

        next(exchange, context);
    }
}
