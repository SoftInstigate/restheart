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

import com.mongodb.DBObject;
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
import org.restheart.utils.JsonUtils;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
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
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        Bson query = eq("_id", context.getDocumentId());

        if (context.getShardKey() != null) {
            query = and(query, context.getShardKey());
        }
        
        final BsonDocument fieldsToReturn = new BsonDocument();

        Deque<String> keys = context.getKeys();

        if (keys != null) {
            keys.stream().forEach((String f) -> {
                // this can throw JSONParseException for invalid filter parameters
                BsonDocument keyQuery = BsonDocument.parse(f);

                fieldsToReturn.putAll(keyQuery);  
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
                    : " '".concat(
                            context.getDocumentId().toString()
                            .concat("' does not exist"));

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

            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, errMsg);
            return;
        }

        Object etag = document.get("_etag");

        // in case the request contains the IF_NONE_MATCH header with the current etag value,
        // just return 304 NOT_MODIFIED code
        if (RequestHelper.checkReadEtag(exchange, (BsonObjectId) etag)) {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_MODIFIED);
            return;
        }

        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());

        ResponseHelper.injectEtagHeader(exchange, etag);
        exchange.setStatusCode(HttpStatus.SC_OK);

        DocumentRepresentationFactory drp = new DocumentRepresentationFactory();
        
        // 
        DBObject data = JsonUtils.convertBsonValueToDBObject(document);
        
        Representation rep = drp.getRepresentation(requestPath, exchange, context,
                data);

        exchange.setStatusCode(HttpStatus.SC_OK);

        // call the ResponseTranformerMetadataHandler if piped in
        if (getNext() != null) {
            DBObject responseContent = rep.asDBObject();
            context.setResponseContent(responseContent);

            getNext().handleRequest(exchange, context);
        }

        drp.sendRepresentation(exchange, context, rep);
        exchange.endExchange();
    }
}
