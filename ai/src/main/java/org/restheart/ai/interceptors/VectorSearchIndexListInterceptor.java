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
import org.bson.BsonString;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * Intercepts GET /_indexes responses and appends any Atlas Vector Search
 * indexes to the list already returned by the standard GetIndexesHandler.
 *
 * <p>Each vector search index document is enriched with {@code "type": "vectorSearch"}
 * so that callers can distinguish it from ordinary MongoDB indexes.
 *
 * <p>The interceptor silently no-ops on deployments that do not support
 * Atlas Search (e.g. Community Edition without Atlas Search enabled).
 */
@RegisterPlugin(
    name = "vectorSearchIndexListInterceptor",
    description = "Appends Atlas Vector Search indexes to GET /_indexes responses",
    interceptPoint = InterceptPoint.RESPONSE,
    requiresContent = true
)
public class VectorSearchIndexListInterceptor implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorSearchIndexListInterceptor.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isCollectionIndexes()
            && request.isGet()
            && !response.isInError();
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var indexes = response.getContent();
        if (indexes == null || !indexes.isArray()) {
            return;
        }

        try {
            mclient.getDatabase(request.getDBName())
                .getCollection(request.getCollectionName(), BsonDocument.class)
                .listSearchIndexes()
                .forEach(doc -> {
                    var bi = BsonDocument.parse(doc.toJson());
                    var name = bi.remove("name");
                    if (name != null) {
                        bi.put("_id", name);
                    }
                    bi.put("type", new BsonString("vectorSearch"));
                    indexes.asArray().add(bi);
                });

            response.setCount(indexes.asArray().size());
        } catch (Exception e) {
            // listSearchIndexes is only available on Atlas / MongoDB >= 7.0 with Atlas Search.
            // Skip so that standard index listing still works on Community Edition.
            LOGGER.warn("Could not list vector search indexes for {}/{}: {}",
                request.getDBName(), request.getCollectionName(), e.getMessage());
        }
    }
}
