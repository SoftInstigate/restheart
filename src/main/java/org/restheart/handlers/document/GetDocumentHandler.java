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
package org.restheart.handlers.document;

import static com.mongodb.client.model.Filters.eq;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.restheart.hal.Representation;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import org.restheart.utils.JsonUtils;
import org.restheart.handlers.RequestContext.TYPE;
import static com.mongodb.client.model.Filters.and;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetDocumentHandler extends PipedHttpHandler {

    /**
     * Default ctor
     */
    public GetDocumentHandler() {
        super();
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public GetDocumentHandler(PipedHttpHandler next) {
        super(next);
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
            RequestContext context)
            throws Exception {
        Bson query = eq("_id", context.getDocumentId());

        if (context.getShardKey() != null) {
            query = and(query, context.getShardKey());
        }

        final BsonDocument fieldsToReturn = new BsonDocument();

        Deque<String> keys = context.getKeys();

        if (keys != null) {
            keys.stream().forEach((String f) -> {
                BsonDocument keyQuery = BsonDocument.parse(f);

                fieldsToReturn.putAll(keyQuery);  // this can throw JSONParseException for invalid filter parameters
            });
        }

        BsonDocument document = getDatabase().getCollection(
                context.getDBName(),
                context.getCollectionName())
                .find(query)
                .projection(fieldsToReturn)
                .first();

        if (document == null) {
            String errMsg = context.getDocumentId() == null
                    ? " does not exist"
                    : " ".concat(JsonUtils.getIdAsString(
                            context.getDocumentId()))
                    .concat(" does not exist");

            if (null != context.getType()) {
                switch (context.getType()) {
                    case DOCUMENT:
                        errMsg = "document".concat(errMsg);
                        break;
                    case FILE:
                        errMsg = "file".concat(errMsg);
                        break;
                    case SCHEMA:
                        errMsg = "schema".concat(errMsg);
                        break;
                    default:
                        errMsg = "resource".concat(errMsg);
                        break;
                }
            }

            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_FOUND,
                    errMsg);
            return;
        }

        Object etag;

        if (context.getType() == TYPE.FILE) {
            if (document.containsKey("metadata")
                    && document.get("metadata").isDocument()) {
                etag = document.get("metadata").asDocument().get(("_etag"));
            } else if (document.containsKey("_etag")) {
                // backward compatibility. until version 2.0.x, _etag was not
                // in the metadata sub-document
                etag = document.get("_etag");
            } else {
                etag = null;
            }
        } else {
            etag = document.get("_etag");
        }

        // in case the request contains the IF_NONE_MATCH header with the current etag value,
        // just return 304 NOT_MODIFIED code
        if (RequestHelper.checkReadEtag(exchange, (BsonObjectId) etag)) {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_MODIFIED);
            return;
        }

        String requestPath = URLUtils.removeTrailingSlashes(
                exchange.getRequestPath());

        context.setResponseContent(new DocumentRepresentationFactory()
                .getRepresentation(
                        requestPath,
                        exchange,
                        context,
                        document)
                .asBsonDocument());

        context.setResponseContentType(Representation.HAL_JSON_MEDIA_TYPE);
        context.setResponseStatusCode(HttpStatus.SC_OK);

        ResponseHelper.injectEtagHeader(exchange, etag);

        // call the ResponseTransformerMetadataHandler if piped in
        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }
}
