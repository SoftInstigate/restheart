/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.interceptors;

import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * Intercepts DELETE /_indexes/{name} requests and drops the named Atlas Vector
 * Search index when it exists.
 *
 * <p>The interceptor first checks whether a vector search index with the
 * requested name actually exists. If so, it drops it and returns 204, preventing
 * the standard {@code DeleteIndexHandler} from attempting a regular index drop.
 * If no matching vector search index is found the interceptor is a no-op and
 * the standard handler proceeds normally.
 *
 * <p>Requires MongoDB Atlas or MongoDB >= 7.0 with Atlas Search enabled.
 */
@RegisterPlugin(
    name = "vectorSearchIndexDeleteInterceptor",
    description = "Drops Atlas Vector Search indexes via DELETE /_indexes/{name}",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    priority = Integer.MIN_VALUE
)
public class VectorSearchIndexDeleteInterceptor implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorSearchIndexDeleteInterceptor.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isIndex() && request.isDelete();
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var indexName  = request.getIndexId();
        var collection = mclient.getDatabase(request.getDBName())
            .getCollection(request.getCollectionName(), BsonDocument.class);

        boolean isVectorSearchIndex;
        try {
            isVectorSearchIndex = collection.listSearchIndexes()
                .name(indexName)
                .first() != null;
        } catch (Exception e) {
            // Atlas Search not available on this deployment – let the standard handler run.
            LOGGER.warn("Could not check vector search indexes for {}/{}: {}",
                request.getDBName(), request.getCollectionName(), e.getMessage());
            return;
        }

        if (!isVectorSearchIndex) {
            // Not a vector search index: let DeleteIndexHandler handle it.
            return;
        }

        try {
            collection.dropSearchIndex(indexName);
            LOGGER.info("Dropped vector search index '{}' on {}/{}", indexName,
                request.getDBName(), request.getCollectionName());
        } catch (Exception e) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "error dropping vector search index", e);
            return;
        }

        // Success – prevent standard DeleteIndexHandler from running.
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
        request.setInError(true);
    }
}
