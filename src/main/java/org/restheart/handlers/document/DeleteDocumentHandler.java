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

import org.restheart.db.DocumentDAO;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.RequestHelper;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DeleteDocumentHandler extends PipedHttpHandler {

    private final DocumentDAO documentDAO;

    /**
     * Default ctor
     */
    public DeleteDocumentHandler() {
        this(new DocumentDAO());
    }

    /**
     * Creates a new instance of DeleteDocumentHandler
     *
     * @param documentDAO
     */
    public DeleteDocumentHandler(DocumentDAO documentDAO) {
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
        ObjectId etag = RequestHelper.getWriteEtag(exchange);

        int httpCode = this.documentDAO
                .deleteDocument(context.getDBName(), context.getCollectionName(), context.getDocumentId(), etag);

        // send the warnings if any (and in case no_content change the return code to ok
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            sendWarnings(httpCode, exchange, context);
        } else {
            exchange.setResponseCode(httpCode);
        }

        exchange.endExchange();
    }
}
