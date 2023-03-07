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

import com.mongodb.MongoWriteException;
import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.GridFs;
import org.restheart.mongodb.db.OperationResult;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class PutFileHandler extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PutFileHandler.class);

    private final GridFs gridFs = GridFs.get();

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
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        // must be an object
        if (request.getContent() == null || !request.getContent().isDocument()) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "metadata must be a JSON object");
            next(exchange);
            return;
        }

        var metadata = request.getContent().asDocument();

        var id = request.getDocumentId();

        if (metadata.containsKey("_id") && !metadata.get("_id").equals(id)) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "_id in content body is different than id in URL");
            next(exchange);
            return;
        }

        metadata.put("_id", id);

        OperationResult result;

        try {
            if (request.getFileInputStream() != null) {
                result = gridFs.upsertFile(
                    request.rsOps(),
                    request.getDBName(),
                    request.getCollectionName(),
                    metadata,
                    request.getFileInputStream(),
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
            if (t.getCode() == 11000) {
                var errMsg = "error updating the file, the file bucket might have orphaned chunks";
                LOGGER.error(errMsg, t);
                response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, errMsg);
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
