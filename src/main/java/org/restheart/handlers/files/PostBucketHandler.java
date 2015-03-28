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
import io.undertow.util.HttpString;
import org.bson.types.ObjectId;
import org.restheart.db.Database;
import org.restheart.db.GridFsDAO;
import org.restheart.db.GridFsRepository;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import static org.restheart.utils.URLUtils.getReferenceLink;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class PostBucketHandler extends PipedHttpHandler {
    private final GridFsRepository gridFsDAO;

    public PostBucketHandler() {
        super();
        this.gridFsDAO = new GridFsDAO();
    }

    public PostBucketHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
        this.gridFsDAO = new GridFsDAO();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        final DBObject props = context.getContent();

        Object _id = props.get("_id");

        // id
        if (_id == null) {
            _id = new ObjectId();
        }

        int code;

        try {
            if (context.getFile() != null) {
                code = gridFsDAO.createFile(getDatabase(), context.getDBName(), context.getCollectionName(), _id, props, context.getFile());
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

        // insert the Location handler
        exchange.getResponseHeaders()
                .add(HttpString.tryFromString("Location"),
                        getReferenceLink(context, exchange.getRequestURL(), _id));

        exchange.setResponseCode(code);

        exchange.endExchange();
    }
}
