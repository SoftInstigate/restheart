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
package org.restheart.handlers.bulk;

import io.undertow.server.HttpServerExchange;
import org.restheart.db.BulkOperationResult;
import org.restheart.db.DocumentDAO;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkDeleteDocumentsHandler extends PipedHttpHandler {

    private final DocumentDAO documentDAO;

    /**
     * Creates a new instance of BulkDeleteDocumentsHandler
     *
     */
    public BulkDeleteDocumentsHandler() {
        this(new DocumentDAO());
    }

    /**
     * Creates a new instance of BulkDeleteDocumentsHandler
     *
     * @param documentDAO
     */
    public BulkDeleteDocumentsHandler(DocumentDAO documentDAO) {
        super(null);
        this.documentDAO = documentDAO;
    }

    /**
     * Creates a new instance of BulkDeleteDocumentsHandler
     *
     * @param next
     */
    public BulkDeleteDocumentsHandler(PipedHttpHandler next) {
        super(next);
        this.documentDAO = new DocumentDAO();
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }
        
        BulkOperationResult result = this.documentDAO
                .bulkDeleteDocuments(
                        context.getClientSession(),
                        context.getDBName(),
                        context.getCollectionName(),
                        context.getFiltersDocument(),
                        context.getShardKey());

        context.setDbOperationResult(result);

        context.setResponseStatusCode(result.getHttpCode());

        BulkResultRepresentationFactory bprf = new BulkResultRepresentationFactory();

        context.setResponseContent(bprf.getRepresentation(
                exchange, context, result)
                .asBsonDocument());

        next(exchange, context);
    }
}
