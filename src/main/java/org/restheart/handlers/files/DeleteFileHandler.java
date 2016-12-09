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
package org.restheart.handlers.files;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonValue;
import org.restheart.db.GridFsDAO;
import org.restheart.db.GridFsRepository;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteFileHandler extends PipedHttpHandler {

    private final GridFsRepository gridFsDAO;

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
    public DeleteFileHandler(PipedHttpHandler next) {
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
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }
        
        BsonValue id = context.getDocumentId();

        OperationResult result = this.gridFsDAO
                .deleteFile(getDatabase(), context.getDBName(),
                        context.getCollectionName(), id,
                        context.getETag(),
                        context.isETagCheckRequired());

        context.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_CONFLICT,
                    "The file's ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header");
            next(exchange, context);
            return;
        } 
        
        context.setResponseStatusCode(result.getHttpCode());
        
        next(exchange, context);
    }
}
