/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.types.ObjectId;
import org.restheart.db.OperationResult;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.Relationship;
import org.restheart.hal.metadata.RepresentationTransformer;
import org.restheart.hal.metadata.RequestChecker;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PatchCollectionHandler extends PipedHttpHandler {

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getDBName().isEmpty()) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                    "wrong request, db name cannot be empty");
            return;
        }

        if (context.getCollectionName().isEmpty() || context.getCollectionName().startsWith("_")) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                    "wrong request, collection name cannot be empty or start with _");
            return;
        }

        DBObject content = context.getContent();

        if (content == null) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            return;
        }

        // cannot PATCH with an array
        if (content instanceof BasicDBList) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                    "data cannot be an array");
            return;
        }

        // check RELS metadata
        if (content.containsField(Relationship.RELATIONSHIPS_ELEMENT_NAME)) {
            try {
                Relationship.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong relationships definition. " + ex.getMessage(), ex);
                return;
            }
        }
        
        // check RT metadata
        if (content.containsField(RepresentationTransformer.RTS_ELEMENT_NAME)) {
            try {
                RepresentationTransformer.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong representation transformer definition. " + ex.getMessage(), ex);
                return;
            }
        }
        
        // check SC metadata
        if (content.containsField(RequestChecker.SCS_ELEMENT_NAME)) {
            try {
                RequestChecker.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong schema checker definition. " + ex.getMessage(), ex);
                return;
            }
        }

        ObjectId etag = RequestHelper.getWriteEtag(exchange);

        if (etag == null) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_CONFLICT,
                    "The collection's ETag must be provided using the '" + Headers.IF_MATCH + "' header");
            return;
        }

        OperationResult result = getDatabase().upsertCollection(context.getDBName(), context.getCollectionName(), content, etag, true, true);

        if (result.getEtag() != null) {
            exchange.getResponseHeaders().put(Headers.ETAG, result.getEtag().toString());
        }
        
        // send the warnings if any (and in case no_content change the return code to ok
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            sendWarnings(result.getHttpCode(), exchange, context);
        } else {
            exchange.setResponseCode(result.getHttpCode());
        }
        
        exchange.endExchange();

        LocalCachesSingleton.getInstance().invalidateCollection(context.getDBName(), context.getCollectionName());
    }

}
