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
package org.restheart.handlers.document;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.DocumentDAO;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;

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
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        
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
            ResponseHelper.endExchangeWithMessage(
                    exchange,
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
