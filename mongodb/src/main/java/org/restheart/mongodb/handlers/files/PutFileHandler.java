/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.db.GridFs;
import org.restheart.mongodb.db.OperationResult;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class PutFileHandler extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PutFileHandler.class);

    private final GridFs gridFs = GridFs.get();;
    private final Databases dbs = Databases.get();

    /**
     *
     */
    public PutFileHandler() {
        this(null);
    }

    /**
     *
     * @param next
     */
    public PutFileHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange)
            throws Exception {
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

        var id = request.getDocumentId();

        if (metadata.get("_id") != null && metadata.get("_id").equals(id)) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "_id in content body is different than id in URL");
            next(exchange);
            return;
        }

        metadata.put("_id", id);

        OperationResult result;

        try {
            if (request.getFilePath() != null) {
                result = gridFs.upsertFile(
                    dbs.db(request.rsOps(), request.getDBName()),
                    request.getCollectionName(),
                    metadata,
                    request.getFilePath(),
                    id,
                    request.getFiltersDocument(),
                    request.getETag(),
                    request.isETagCheckRequired());
            } else {
                // throw new RuntimeException("error. file data is null");
                // try to pass to next handler in order to PUT new metadata on existing file.
                next(exchange);
                return;
            }
        } catch (MongoWriteException t) {
            if (((MongoException) t).getCode() == 11000) {
                // update not supported
                String errMsg = "file resource update is not yet implemented";
                LOGGER.error(errMsg, t);
                response.setInError(HttpStatus.SC_NOT_IMPLEMENTED, errMsg);
                next(exchange);
                return;
            }

            throw t;
        }

        response.setDbOperationResult(result);

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
