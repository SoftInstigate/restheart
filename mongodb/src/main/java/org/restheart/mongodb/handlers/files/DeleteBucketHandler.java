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

import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.GridFs;
import org.restheart.mongodb.handlers.collection.DeleteCollectionHandler;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteBucketHandler extends DeleteCollectionHandler {

    private final GridFs gridFs = GridFs.get();

    /**
     * Creates a new instance of DeleteBucketHandler
     *
     */
    public DeleteBucketHandler() {
        super();
    }

    /**
     * Creates a new instance of DeleteBucketHandler
     *
     * @param next
     */
    public DeleteBucketHandler(PipelinedHandler next) {
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

        try {
            gridFs.deleteChunksCollection(
                request.rsOps(),
                request.getDBName(),
                request.getCollectionName());
        } catch (Throwable t) {
            response.addWarning("error removing the bucket file chunks: " + t.getMessage());
        }

        // delete the bucket collection
        super.handleRequest(exchange);
    }
}
