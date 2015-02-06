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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.utils.HttpStatus;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import org.bson.types.ObjectId;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GetDocumentHandler extends PipedHttpHandler {

    /**
     * Default ctor
     */
    public GetDocumentHandler() {
        super();
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        BasicDBObject query = new BasicDBObject("_id", context.getDocumentId());

        DBObject document = getDatabase().getCollection(context.getDBName(), context.getCollectionName()).findOne(query);

        if (document == null) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "document does not exist");
            return;
        }

        Object etag = document.get("_etag");

        if (etag != null && ObjectId.isValid("" + etag)) {
            ObjectId _etag = new ObjectId("" + etag);

            document.put("_lastupdated_on", Instant.ofEpochSecond(_etag.getTimestamp()).toString());

            // in case the request contains the IF_NONE_MATCH header with the current etag value,
            // just return 304 NOT_MODIFIED code
            if (RequestHelper.checkReadEtag(exchange, etag.toString())) {
                ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_MODIFIED);
                return;
            }
        }

        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());

        ResponseHelper.injectEtagHeader(exchange, document);
        exchange.setResponseCode(HttpStatus.SC_OK);

        DocumentRepresentationFactory.sendDocument(requestPath, exchange, context, document);
        exchange.endExchange();
    }
}
