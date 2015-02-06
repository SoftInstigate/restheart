/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.RequestHelper;
import org.restheart.db.GridFsDAO;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;
import org.restheart.db.GridFsRepository;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DeleteFileHandler extends PipedHttpHandler {

    private final GridFsRepository gridFsDAO;

    /**
     * Default constructor
     */
    public DeleteFileHandler() {
        this(new GridFsDAO());
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
        ObjectId etag = RequestHelper.getWriteEtag(exchange);

        int httpCode = this.gridFsDAO
                .deleteFile(getDatabase(), context.getDBName(), context.getCollectionName(), context.getDocumentId(), etag);

        // send the warnings if any (and in case no_content change the return code to ok)
        if (context.getWarnings() != null && !context.getWarnings().isEmpty()) {
            sendWarnings(httpCode, exchange, context);
        } else {
            if (httpCode == HttpStatus.SC_CONFLICT) {
                ResponseHelper.endExchangeWithMessage(exchange, httpCode, "the ETag header must be provided");
            } else {
                ResponseHelper.endExchange(exchange, httpCode);
            }
        }
    }
}