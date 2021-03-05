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
package org.restheart.mongodb.handlers.bulk;

import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.BulkOperationResult;
import org.restheart.mongodb.db.DocumentDAO;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkDeleteDocumentsHandler extends PipelinedHandler {

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
    public BulkDeleteDocumentsHandler(PipelinedHandler next) {
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
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        BulkOperationResult result = this.documentDAO
                .bulkDeleteDocuments(
                        request.getClientSession(),
                        request.getDBName(),
                        request.getCollectionName(),
                        request.getFiltersDocument(),
                        request.getShardKey());

        response.setDbOperationResult(result);

        response.setStatusCode(result.getHttpCode());

        BulkResultRepresentationFactory bprf = new BulkResultRepresentationFactory();

        response.setContent(bprf.getRepresentation(request.getPath(), result));

        next(exchange);
    }
}
