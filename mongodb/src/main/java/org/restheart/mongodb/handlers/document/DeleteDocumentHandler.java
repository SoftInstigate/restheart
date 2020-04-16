/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.handlers.document;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.exchange.OperationResult;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.DocumentDAO;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteDocumentHandler extends PipelinedHandler {

    private final DocumentDAO documentDAO;

    /**
     * Creates a new instance of DeleteDocumentHandler
     *
     */
    public DeleteDocumentHandler() {
        super(null);
        this.documentDAO = new DocumentDAO();
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
     * Creates a new instance of DeleteDocumentHandler
     *
     * @param next
     * @param documentDAO
     */
    public DeleteDocumentHandler(PipelinedHandler next, DocumentDAO documentDAO) {
        super(next);
        this.documentDAO = documentDAO;
    }

    /**
     * Default ctor
     * @param next
     */
    public DeleteDocumentHandler(PipelinedHandler next) {
        super(next);
        this.documentDAO = new DocumentDAO();
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        
        if (request.isInError()) {
            next(exchange);
            return;
        }
        
        OperationResult result = this.documentDAO
                .deleteDocument(
                        request.getClientSession(),
                        request.getDBName(),
                        request.getCollectionName(),
                        request.getDocumentId(),
                        request.getFiltersDocument(),
                        request.getShardKey(),
                        request.getETag(),
                        request.isETagCheckRequired());

        response.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            response.setIError(
                    HttpStatus.SC_CONFLICT,
                    "The document's ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header");
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
