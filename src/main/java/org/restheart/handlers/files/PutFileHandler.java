/*
 * RESTHeart - the data REST API server
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

import com.mongodb.DBObject;
import com.mongodb.DuplicateKeyException;
import io.undertow.server.HttpServerExchange;
import org.restheart.db.Database;
import org.restheart.db.GridFsDAO;
import org.restheart.db.GridFsRepository;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class PutFileHandler extends PipedHttpHandler {
    private final GridFsRepository gridFsDAO;

    public PutFileHandler() {
        super();
        this.gridFsDAO = new GridFsDAO();
    }

    public PutFileHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
        this.gridFsDAO = new GridFsDAO();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {

        final DBObject content = context.getContent();

        Object id = context.getDocumentId();

        if (content.get("_id") == null) {
            content.put("_id", id);
        } else if (!content.get("_id").equals(id)) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "_id in content body is different than id in URL");
            return;
        }

        int code;
        
        try {
            if (context.getFile() != null) {
                code = gridFsDAO.createFile(getDatabase(), context.getDBName(), context.getCollectionName(), id, content, context.getFile());
            } else {
                throw new RuntimeException("error. file data is null");
            }
        } catch (Throwable t) {
            if (t instanceof DuplicateKeyException) {
                // update not supported
                String errMsg = "file resource update is not yet implemented";
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_IMPLEMENTED, errMsg);
                return;
            }

            throw t;
        }

        exchange.setResponseCode(code);
        exchange.endExchange();
    }
}