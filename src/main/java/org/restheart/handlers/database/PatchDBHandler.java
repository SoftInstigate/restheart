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
package org.restheart.handlers.database;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.handlers.metadata.InvalidMetadataException;
import org.restheart.metadata.TransformerMetadata;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PatchDBHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of PatchDBHandler
     */
    public PatchDBHandler() {
        super();
    }

    /**
     * Creates a new instance of PatchDBHandler
     *
     * @param next
     */
    public PatchDBHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     * partial update db properties
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        if (context.getDBName().isEmpty()
                || context.getDBName().startsWith("_")) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "wrong request, db name cannot be empty or start with _");
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

        // cannot PATCH an array
        if (!_content.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data must be a json object");
            next(exchange, context);
            return;
        }

        BsonDocument content = _content.asDocument();

        // check RTL metadata
        if (content.containsKey(TransformerMetadata.RTS_ELEMENT_NAME)) {
            try {
                TransformerMetadata.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong representation transform logic definition. "
                        + ex.getMessage(),
                        ex);
                next(exchange, context);
                return;
            }
        }

        OperationResult result = getDatabase().upsertDB(
                context.getClientSession(),
                context.getDBName(),
                content,
                context.getETag(),
                true,
                true,
                context.isETagCheckRequired());

        context.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_CONFLICT,
                    "The database's ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header.");
            next(exchange, context);
            return;
        }

        context.setResponseStatusCode(result.getHttpCode());

        LocalCachesSingleton.getInstance().invalidateDb(context.getDBName());

        next(exchange, context);
    }
}
