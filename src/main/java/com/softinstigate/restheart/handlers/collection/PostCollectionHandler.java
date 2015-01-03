/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package com.softinstigate.restheart.handlers.collection;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.DocumentDAO;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.handlers.document.DocumentRepresentationFactory;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.net.URISyntaxException;
import org.bson.types.ObjectId;

/**
 *
 * @author Andrea Di Cesare
 */
public class PostCollectionHandler extends PutCollectionHandler {

    /**
     * Creates a new instance of PostCollectionHandler
     */
    public PostCollectionHandler() {
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

        int SC = DocumentDAO.upsertDocumentPost(exchange, context.getDBName(), context.getCollectionName(), content, etag);

        // send the warnings if any (and in case no_content change the return code to ok
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            sendWarnings(SC, exchange, context);
        } else {
            exchange.setResponseCode(SC);
        }

        exchange.endExchange();
    }

    private void sendWarnings(int SC, HttpServerExchange exchange, RequestContext context) throws URISyntaxException, IllegalQueryParamenterException {
        if (SC == HttpStatus.SC_NO_CONTENT) {
            exchange.setResponseCode(HttpStatus.SC_OK);
        } else {
            exchange.setResponseCode(SC);
        }
        
        DocumentRepresentationFactory.sendDocument(exchange.getRequestPath(), exchange, context, new BasicDBObject());
    }
}
