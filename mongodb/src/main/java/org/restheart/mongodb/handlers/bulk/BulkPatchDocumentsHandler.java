/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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

import java.util.Optional;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Documents;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkPatchDocumentsHandler extends PipelinedHandler {

    private final Documents documents = Documents.get();

    /**
     * Creates a new instance of PatchDocumentHandler
     */
    public BulkPatchDocumentsHandler() {
        this(null);
    }

    /**
     *
     * @param next
     */
    public BulkPatchDocumentsHandler(PipelinedHandler next) {
        super(next);
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

        var result = this.documents.bulkPatchDocuments(
            Optional.ofNullable(request.getClientSession()),
            request.rsOps(), 
            request.getDBName(),
            request.getCollectionName(),
            request.getFiltersDocument(),
            Optional.ofNullable(request.getShardKey()),
            request.getContent().asDocument());

        response.setDbOperationResult(result);

        response.setStatusCode(result.getHttpCode());

        var bprf = new BulkResultRepresentationFactory();

        response.setContent(bprf.getRepresentation(request.getPath(), result));

        next(exchange);
    }
}
