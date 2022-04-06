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
package org.restheart.mongodb.handlers.document;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.util.Optional;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.db.Documents;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteDocumentHandler extends PipelinedHandler {
    private final Documents documents = Documents.get();
    private final Databases dbs = Databases.get();

    /**
     * Creates a new instance of DeleteDocumentHandler
     *
     */
    public DeleteDocumentHandler() {
        super(null);
    }

    /**
     * Creates a new instance of DeleteDocumentHandler
     *
     * @param next
     * @param documentDAO
     */
    public DeleteDocumentHandler(PipelinedHandler next) {
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

        var result = documents.deleteDocument(
            Optional.ofNullable(request.getClientSession()),
            dbs.db(request.rsOps(), request.getDBName()),
            request.getCollectionName(),
            Optional.of(request.getDocumentId()),
            Optional.ofNullable(request.getFiltersDocument()),
            Optional.ofNullable(request.getShardKey()),
            request.getETag(),
            request.isETagCheckRequired());

        response.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            response.setInError(HttpStatus.SC_CONFLICT, "The document's ETag must be provided using the '" + Headers.IF_MATCH + "' header");
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
