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
package org.restheart.mongodb.handlers.bulk;

import io.undertow.server.HttpServerExchange;
import org.restheart.mongodb.db.BulkOperationResult;
import org.restheart.mongodb.db.DocumentDAO;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkPatchDocumentsHandler extends PipelinedHandler {

    private final DocumentDAO documentDAO;

    /**
     * Creates a new instance of PatchDocumentHandler
     */
    public BulkPatchDocumentsHandler() {
        this(null, new DocumentDAO());
    }

    /**
     *
     * @param documentDAO
     */
    public BulkPatchDocumentsHandler(DocumentDAO documentDAO) {
        super(null);
        this.documentDAO = documentDAO;
    }
    
    /**
     *
     * @param next
     */
    public BulkPatchDocumentsHandler(PipelinedHandler next) {
        super(next);
        this.documentDAO = new DocumentDAO();
    }
    
    /**
     *
     * @param next
     * @param documentDAO
     */
    public BulkPatchDocumentsHandler(PipelinedHandler next, DocumentDAO documentDAO) {
        super(next);
        this.documentDAO = documentDAO;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
                
        if (request.isInError()) {
            next(exchange);
            return;
        }
        
        BulkOperationResult result = this.documentDAO
                .bulkPatchDocuments(
                        request.getClientSession(),
                        request.getDBName(), 
                        request.getCollectionName(), 
                        request.getFiltersDocument(),
                        request.getShardKey(),
                        request.getContent().asDocument());

        response.setDbOperationResult(result);

        response.setStatusCode(result.getHttpCode());

        BulkResultRepresentationFactory bprf = new BulkResultRepresentationFactory();

        response.setContent(bprf.getRepresentation(
                exchange, result)
                .asBsonDocument());

        next(exchange);
    }
}
