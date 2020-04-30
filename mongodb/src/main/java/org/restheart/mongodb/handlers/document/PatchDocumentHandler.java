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
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.OperationResult;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.DocumentDAO;
import org.restheart.mongodb.utils.RequestHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PatchDocumentHandler extends PipelinedHandler {

    private final DocumentDAO documentDAO;

    /**
     * Creates a new instance of PatchDocumentHandler
     */
    public PatchDocumentHandler() {
        this(null, new DocumentDAO());
    }

    public PatchDocumentHandler(DocumentDAO documentDAO) {
        super(null);
        this.documentDAO = documentDAO;
    }

    public PatchDocumentHandler(PipelinedHandler next) {
        super(next);
        this.documentDAO = new DocumentDAO();
    }

    public PatchDocumentHandler(PipelinedHandler next, DocumentDAO documentDAO) {
        super(next);
        this.documentDAO = documentDAO;
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

        BsonValue _content = request.getContent();

        if (RequestHelper.isNotAcceptableContent(_content, exchange)) {
            next(exchange);
            return;
        }

        BsonDocument content = _content.asDocument();

        BsonValue id = request.getDocumentId();

        if (content.get("_id") == null) {
            content.put("_id", id);
        } else if (!content.get("_id").equals(id)) {
            response.setInError(
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "_id in json data cannot be different than id in URL");
            next(exchange);
            return;
        }

        OperationResult result = documentDAO.upsertDocument(
                request.getClientSession(),
                request.getDBName(),
                request.getCollectionName(),
                request.getDocumentId(),
                request.getFiltersDocument(),
                request.getShardKey(),
                content,
                request.getETag(),
                true,
                request.isETagCheckRequired());

        if (RequestHelper.isResponseInConflict(result, exchange)) {
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }

}
