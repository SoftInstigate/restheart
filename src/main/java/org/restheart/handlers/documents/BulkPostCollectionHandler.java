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
package org.restheart.handlers.documents;

import org.restheart.handlers.collection.*;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.bson.types.ObjectId;
import org.restheart.db.DocumentDAO;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.DOC_ID_TYPE;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import static org.restheart.utils.URLUtils.getReferenceLink;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BulkPostCollectionHandler extends PipedHttpHandler {

    private final DocumentDAO documentDAO;

    /**
     * Creates a new instance of PostCollectionHandler
     */
    public BulkPostCollectionHandler() {
        this(null, new DocumentDAO());
    }

    public BulkPostCollectionHandler(DocumentDAO documentDAO) {
        this(null, new DocumentDAO());
    }
    
    public BulkPostCollectionHandler(PipedHttpHandler next) {
        this(next, new DocumentDAO());
    }
    
    public BulkPostCollectionHandler(PipedHttpHandler next, DocumentDAO documentDAO) {
        super(next);
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
        if (!(content instanceof BasicDBList)) {
            throw new RuntimeException("error, this handler expects an array of objects");
        }

        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            //sendWarnings(result.getHttpCode(), exchange, context);
        } else {
            exchange.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        }
        
        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
        
        exchange.endExchange();
    }
}