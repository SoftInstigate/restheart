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
package org.restheart.mongodb.handlers.files;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonValue;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.db.GridFsDAO;
import org.restheart.mongodb.db.GridFsRepository;
import org.restheart.mongodb.db.OperationResult;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.utils.HttpStatus;
import org.restheart.mongodb.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteFileHandler extends PipelinedHandler {

    private final GridFsRepository gridFsDAO;
    private final DatabaseImpl dbsDAO = new DatabaseImpl();

    /**
     * Creates a new instance of DeleteFileHandler
     *
     */
    public DeleteFileHandler() {
        this(new GridFsDAO());
    }

    /**
     * Creates a new instance of DeleteFileHandler
     *
     * @param next
     */
    public DeleteFileHandler(PipelinedHandler next) {
        super(next);
        this.gridFsDAO = new GridFsDAO();
    }

    /**
     * Creates a new instance of DeleteFileHandler
     *
     * @param gridFsDAO
     */
    public DeleteFileHandler(GridFsRepository gridFsDAO) {
        super(null);
        this.gridFsDAO = gridFsDAO;
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
        
        BsonValue id = request.getDocumentId();

        OperationResult result = this.gridFsDAO
                .deleteFile(dbsDAO, request.getDBName(),
                        request.getCollectionName(), id,
                        request.getETag(),
                        request.isETagCheckRequired());

        response.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_CONFLICT,
                    "The file's ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header");
            next(exchange);
            return;
        } 
        
        response.setStatusCode(result.getHttpCode());
        
        next(exchange);
    }
}
