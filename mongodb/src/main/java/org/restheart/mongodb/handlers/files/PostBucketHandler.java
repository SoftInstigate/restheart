/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import com.mongodb.DuplicateKeyException;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.GridFs;
import org.restheart.mongodb.db.OperationResult;
import org.restheart.mongodb.utils.MongoURLUtils;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.RepresentationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class PostBucketHandler extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostBucketHandler.class);
    private final GridFs gridFs = GridFs.get();

    /**
     *
     */
    public PostBucketHandler() {
        this(null);
    }

    /**
     *
     * @param next
     */
    public PostBucketHandler(PipelinedHandler next) {
        super(next);
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

        // must be an object
        if (!request.getContent().isDocument()) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "data cannot be an array");
            next(exchange);
            return;
        }

        var metadata = request.getContent().asDocument();

        OperationResult result;

        try {
            if (request.getFilePath() != null) {
                result = gridFs.createFile(
                    request.rsOps(),
                    request.getDBName(),
                    request.getCollectionName(),
                    metadata,
                    request.getFilePath());
            } else {
                response.setInError(HttpStatus.SC_BAD_REQUEST, "POST file request is in a bad format");
                next(exchange);
                return;
            }
        } catch (DuplicateKeyException t) {
            // update not supported
            String errMsg = "file resource update is not yet implemented";
            LOGGER.error(errMsg, t);
            response.setInError(HttpStatus.SC_NOT_IMPLEMENTED, errMsg);
            next(exchange);
            return;
        }

        response.setDbOperationResult(result);

        // insert the Location handler
        response.getHeaders().add(HttpString.tryFromString("Location"),
            RepresentationUtils.getReferenceLink(MongoURLUtils.getRemappedRequestURL(exchange), result.getNewId()));

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
