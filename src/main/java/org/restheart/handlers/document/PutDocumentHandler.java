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
package org.restheart.handlers.document;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.restheart.db.DocumentDAO;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.collection.PutCollectionHandler;
import org.restheart.utils.HttpStatus;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare
 */
public class PutDocumentHandler extends PipedHttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(PutCollectionHandler.class);

    private static final BasicDBObject fieldsToReturn;

    static {
        fieldsToReturn = new BasicDBObject();
        fieldsToReturn.put("_id", 1);
        fieldsToReturn.put("_created_on", 1);
    }

    /**
     * Creates a new instance of PutDocumentHandler
     */
    public PutDocumentHandler() {
        super(null);
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

        // cannot PUT an array
        if (content instanceof BasicDBList) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "data cannot be an array");
            return;
        }

        String id = context.getDocumentId();

        if (content.get("_id") == null) {
            content.put("_id", getId(id));
        } else if (!content.get("_id").equals(id)) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "_id in content body is different than id in URL");
            return;
        }

        ObjectId etag = RequestHelper.getWriteEtag(exchange);

        int SC = DocumentDAO.upsertDocument(context.getDBName(), context.getCollectionName(), context.getDocumentId(), content, etag, false);

        // send the warnings if any (and in case no_content change the return code to ok
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            sendWarnings(SC, exchange, context);
        } else {
            exchange.setResponseCode(SC);
        }

        exchange.endExchange();
    }

    private static Object getId(String id) {
        if (ObjectId.isValid(id)) {
            return new ObjectId(id);
        } else {
            // the id is not an object id
            return id;
        }
    }
}
