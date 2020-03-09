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
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.DatabaseImpl;
import org.restheart.db.GridFsDAO;
import org.restheart.db.GridFsRepository;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.representation.RepUtils;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class PostBucketHandler extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostBucketHandler.class);
    private final GridFsRepository gridFsDAO;
    private final DatabaseImpl dbsDAO = new DatabaseImpl();

    /**
     *
     */
    public PostBucketHandler() {
        super();
        this.gridFsDAO = new GridFsDAO();
    }

    /**
     *
     * @param next
     */
    public PostBucketHandler(PipelinedHandler next) {
        super(next);
        this.gridFsDAO = new GridFsDAO();
    }

    /**
     *
     * @param next
     * @param gridFsDAO
     */
    public PostBucketHandler(PipelinedHandler next, GridFsDAO gridFsDAO) {
        super(next);
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

        final BsonValue _metadata = request.getContent();

        // must be an object
        if (!_metadata.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data cannot be an array");
            next(exchange);
            return;
        }

        BsonDocument metadata = _metadata.asDocument();

        OperationResult result;

        try {
            if (request.getFilePath() != null) {
                result = gridFsDAO
                        .createFile(dbsDAO,
                                request.getDBName(),
                                request.getCollectionName(),
                                metadata,
                                request.getFilePath());
            } else {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        HttpStatus.SC_BAD_REQUEST,
                        "POST file request is in a bad format");
                next(exchange);
                return;
            }
        } catch (DuplicateKeyException t) {
            // update not supported
            String errMsg = "file resource update is not yet implemented";
            LOGGER.error(errMsg, t);
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_IMPLEMENTED,
                    errMsg);
            next(exchange);
            return;
        }

        response.setDbOperationResult(result);

        // insert the Location handler
        exchange.getResponseHeaders()
                .add(HttpString.tryFromString("Location"),
                        RepUtils.getReferenceLink(
                                URLUtils.getRemappedRequestURL(exchange),
                                result.getNewId()));

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
