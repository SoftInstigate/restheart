/*
 * RESTHeart - the data REST API server
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
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.restheart.db.DocumentDAO;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.bson.types.ObjectId;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext.DOC_ID_TYPE;
import static org.restheart.utils.URLUtils.getReferenceLink;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PostCollectionHandler extends PipedHttpHandler {

    private final DocumentDAO documentDAO;

    /**
     * Creates a new instance of PostCollectionHandler
     */
    public PostCollectionHandler() {
        this(new DocumentDAO());
    }

    public PostCollectionHandler(DocumentDAO documentDAO) {
        this.documentDAO = documentDAO;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        DBObject content = context.getContent();

        if (content == null) {
            content = new BasicDBObject();
        }

        // cannot POST an array
        if (content instanceof BasicDBList) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "data cannot be an array");
            return;
        }

        ObjectId etag = RequestHelper.getWriteEtag(exchange);

        if (content.get("_id") != null && content.get("_id") instanceof String && RequestContext.isReservedResourceDocument((String) content.get("_id"))) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "reserved resource");
            return;
        }

        Object docId;

        if (content.get("_id") == null) {
            if (context.getDocIdType() == DOC_ID_TYPE.OID || context.getDocIdType() == DOC_ID_TYPE.STRING_OID) {
                docId = new ObjectId();
            } else {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "_id in content body is mandatory for documents with id type " + context.getDocIdType().name());
                return;
            }

        } else {
            docId = content.get("_id");
        }

        int httpCode = this.documentDAO
                .upsertDocumentPost(context.getDBName(), context.getCollectionName(), docId, content, etag);

        // insert the Location handler
        exchange.getResponseHeaders()
                .add(HttpString.tryFromString("Location"),
                        getReferenceLink(context, exchange.getRequestURL(), docId));

        // send the warnings if any (and in case no_content change the return code to ok
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            sendWarnings(httpCode, exchange, context);
        } else {
            exchange.setResponseCode(httpCode);
        }
        
        if (httpCode == HttpStatus.SC_CREATED || httpCode == HttpStatus.SC_OK) {
            content.put("_etag", etag);
            ResponseHelper.injectEtagHeader(exchange, content);
        }

        exchange.endExchange();
    }
}