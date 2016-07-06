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

import com.mongodb.DuplicateKeyException;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.io.IOException;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.GridFsDAO;
import org.restheart.db.GridFsRepository;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import static org.restheart.utils.URLUtils.getReferenceLink;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class PostBucketHandler extends PipedHttpHandler {
    private final GridFsRepository gridFsDAO;

    public PostBucketHandler() {
        super();
        this.gridFsDAO = new GridFsDAO();
    }

    public PostBucketHandler(PipedHttpHandler next) {
        super(next);
        this.gridFsDAO = new GridFsDAO();
    }

    public PostBucketHandler(PipedHttpHandler next, GridFsDAO gridFsDAO) {
        super(next);
        this.gridFsDAO = gridFsDAO;
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        final BsonValue _metadata = context.getContent();

        // must be an object
        if (!_metadata.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data cannot be an array");
            return;
        }

        BsonDocument metadata = _metadata.asDocument();

        OperationResult result;

        try {
            if (context.getFilePath() != null) {
                result = gridFsDAO
                        .createFile(getDatabase(),
                                context.getDBName(),
                                context.getCollectionName(),
                                metadata,
                                context.getFilePath());
            } else {
                throw new RuntimeException("error. file data is null");
            }
        } catch (IOException | RuntimeException t) {
            if (t instanceof DuplicateKeyException) {
                // update not supported
                String errMsg = "file resource update is not yet implemented";
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_NOT_IMPLEMENTED,
                        errMsg);
                return;
            }

            throw t;
        }

        context.setDbOperationResult(result);

        // insert the Location handler
        exchange.getResponseHeaders()
                .add(HttpString.tryFromString("Location"),
                        getReferenceLink(
                                context,
                                exchange.getRequestURL(), result.getNewId()));

        exchange.setStatusCode(result.getHttpCode());

        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }

        exchange.endExchange();
    }
}
