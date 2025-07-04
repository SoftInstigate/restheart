/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import java.util.Optional;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Documents;
import org.restheart.mongodb.utils.RequestHelper;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PatchDocumentHandler extends PipelinedHandler {

    private final Documents documents = Documents.get();

    /**
     * Creates a new instance of PatchDocumentHandler
     */
    public PatchDocumentHandler() {
        this(null);
    }

    public PatchDocumentHandler(PipelinedHandler next) {
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

        var _content = request.getContent();

        if (RequestHelper.isNotAcceptableContentForPatch(_content, exchange)) {
            next(exchange);
            return;
        }

        if (_content.isDocument()) {
            var content = _content.asDocument();
            var id = request.getDocumentId();

            if (content.get("_id") == null) {
                content.put("_id", id);
            } else if (!content.get("_id").equals(id)) {
                response.setInError(HttpStatus.SC_BAD_REQUEST, "_id in json data cannot be different than id in URL");
                next(exchange);
                return;
            }
        }

        var result = documents.writeDocument(
            Optional.ofNullable(request.getClientSession()),
            request.rsOps(),
            request.getDBName(),
            request.getCollectionName(),
            request.getMethod(),
            request.getWriteMode(),
            Optional.of(request.getDocumentId()),
            Optional.ofNullable(request.getFiltersDocument()),
            Optional.ofNullable(request.getShardKey()),
            _content,
            request.getETag(),
            request.isETagCheckRequired());

        response.setDbOperationResult(result);

        if (RequestHelper.isResponseInConflict(result, exchange)) {
            next(exchange);
            return;
        }

        // handle the case of error result with exception
        if (result.getCause() != null) {
            response.setInError(result.getHttpCode(), result.getCause().getMessage());
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }

}
