/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.db.GridFsDAO;
import org.restheart.mongodb.db.GridFsRepository;
import org.restheart.mongodb.handlers.collection.DeleteCollectionHandler;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteBucketHandler extends DeleteCollectionHandler {

    private final GridFsRepository gridFsDAO;
    private final DatabaseImpl dbsDAO = new DatabaseImpl();

    /**
     * Creates a new instance of DeleteBucketHandler
     *
     */
    public DeleteBucketHandler() {
        super();
        this.gridFsDAO = new GridFsDAO();
    }

    /**
     * Creates a new instance of DeleteBucketHandler
     *
     * @param next
     */
    public DeleteBucketHandler(PipelinedHandler next) {
        super(next);
        this.gridFsDAO = new GridFsDAO();
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.of(exchange);
        var response = BsonResponse.of(exchange);
        
        if (request.isInError()) {
            next(exchange);
            return;
        }
        
        try {
            gridFsDAO.deleteChunksCollection(dbsDAO, request.getDBName(), request.getCollectionName());
        } catch (Throwable t) {
            response.addWarning("error removing the bucket file chunks: " + t.getMessage());
        }

        // delete the bucket collection
        super.handleRequest(exchange);
    }
}
