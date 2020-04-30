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
package org.restheart.mongodb.handlers.files;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.OperationResult;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.db.GridFsDAO;
import org.restheart.mongodb.db.GridFsRepository;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

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
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);
        
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
            response.setInError(
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
