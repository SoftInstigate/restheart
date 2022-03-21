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
package org.restheart.mongodb.handlers.collection;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.interceptors.MetadataCachesSingleton;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PutCollectionHandler extends PipelinedHandler {
    private Databases dbs = Databases.get();
    /**
     * Creates a new instance of PutCollectionHandler
     */
    public PutCollectionHandler() {
        super();
    }

    /**
     * Creates a new instance of PutCollectionHandler
     *
     * @param next
     */
    public PutCollectionHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of PutCollectionHandler
     *
     * @param next
     * @param dbsDAO
     */
    @VisibleForTesting
    public PutCollectionHandler(PipelinedHandler next, Databases dbsDAO) {
        super(next);
        this.dbs = dbsDAO;
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

        var _content = request.getContent();

        if (_content == null) {
            _content = new BsonDocument();
        }

        // cannot PUT an array
        if (!_content.isDocument()) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "data must be a json object");
            next(exchange);
            return;
        }

        final BsonDocument content = _content.asDocument();

        var result = dbs.upsertCollection(
            Optional.ofNullable(request.getClientSession()),
            request.getMethod(),
            request.getCollectionProps() != null, // true if updating
            request.getDBName(),
            request.getCollectionName(),
            content,
            request.getETag(),
            request.isETagCheckRequired());

        response.setDbOperationResult(result);

        // invalidate the cache collection item
        MetadataCachesSingleton.getInstance().invalidateCollection(request.getDBName(), request.getCollectionName());

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            response.setInError(HttpStatus.SC_CONFLICT, "The collection's ETag must be provided using the '" + Headers.IF_MATCH + "' header.");
            next(exchange);
            return;
        }

        response.setStatusCode(result.getHttpCode());

        next(exchange);
    }
}
