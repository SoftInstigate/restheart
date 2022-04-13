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
package org.restheart.mongodb.handlers.indexes;

import io.undertow.server.HttpServerExchange;

import java.util.Optional;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Databases;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteIndexHandler extends PipelinedHandler {
    private final Databases dbs = Databases.get();

    /**
     * Creates a new instance of DeleteIndexHandler
     */
    public DeleteIndexHandler() {
        super();
    }

    /**
     * Creates a new instance of DeleteIndexHandler
     *
     * @param next
     */
    public DeleteIndexHandler(PipelinedHandler next) {
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

        var indexId = request.getIndexId();

        if (indexId.startsWith("_") || indexId.equals("_id_")) {
            response.setInError(HttpStatus.SC_UNAUTHORIZED, indexId + " is a default index and cannot be deleted");
            next(exchange);
            return;
        }

        var httpCode = dbs.deleteIndex(
            Optional.ofNullable(request.getClientSession()),
            request.rsOps(),
            request.getDBName(),
            request.getCollectionName(),
            indexId);

        response.setStatusCode(httpCode);

        next(exchange);
    }
}
