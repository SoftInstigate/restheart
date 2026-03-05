/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.handlers.bulk;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.GridFs;
import org.restheart.utils.HttpStatus;

/**
 * Handles bulk DELETE requests for GridFS files: DELETE /db/bucket.files/*?filter={...}
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkDeleteFilesHandler extends PipelinedHandler {

    private final GridFs gridFs = GridFs.get();

    public BulkDeleteFilesHandler() {
    }

    public BulkDeleteFilesHandler(PipelinedHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        long deleted = gridFs.bulkDeleteFiles(
            request.rsOps(),
            request.getDBName(),
            request.getCollectionName(),
            request.getFiltersDocument());

        response.setStatusCode(HttpStatus.SC_OK);
        response.setContent(new BsonDocument("deleted", new BsonInt64(deleted)));

        next(exchange);
    }
}
