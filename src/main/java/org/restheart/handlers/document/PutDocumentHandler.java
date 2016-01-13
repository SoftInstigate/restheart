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
package org.restheart.handlers.document;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.types.ObjectId;
import org.restheart.db.DocumentDAO;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PutDocumentHandler extends PipedHttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PutDocumentHandler.class);

    private final DocumentDAO documentDAO;

    /**
     * Default ctor
     */
    public PutDocumentHandler() {
        this(new DocumentDAO());
    }

    /**
     * Creates a new instance of PutDocumentHandler
     *
     * @param documentDAO
     */
    public PutDocumentHandler(DocumentDAO documentDAO) {
        super(null);
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

        // cannot PUT an array
        if (content instanceof BasicDBList) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "data cannot be an array");
            return;
        }

        Object id = context.getDocumentId();

        if (content.get("_id") == null) {
            content.put("_id", id);
        } else if (!content.get("_id").equals(id)) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "_id in content body is different than id in URL");
            return;
        }
        
        ObjectId etag = RequestHelper.getWriteEtag(exchange);
        
        LOGGER.debug("checking etag check policy {}", context.isETagRequired());
        LOGGER.debug("etag {}", context.getETag());

        OperationResult result = this.documentDAO.upsertDocument(
                context.getDBName(),
                context.getCollectionName(),
                context.getDocumentId(),
                content,
                etag,
                false);

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
    }

}
