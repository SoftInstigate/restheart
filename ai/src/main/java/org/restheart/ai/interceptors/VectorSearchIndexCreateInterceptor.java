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
import org.bson.Document;
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
import com.mongodb.client.model.SearchIndexModel;
import com.mongodb.client.model.SearchIndexType;

/**
 * Intercepts PUT /_indexes/{name} requests that carry a body with
 * {@code "type": "vectorSearch"} and creates an Atlas Vector Search index
 * instead of a standard MongoDB index.
 *
 * <p>Request body example:
 * <pre>{@code
 * PUT /mydb/mycollection/_indexes/myVectorIndex
 * {
 *   "type": "vectorSearch",
 *   "fields": [
 *     {
 *       "type": "vector",
 *       "path": "embedding",
 *       "numDimensions": 1536,
 *       "similarity": "cosine"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>autoEmbed example:
 * <pre>{@code
 * {
 *   "type": "vectorSearch",
 *   "fields": [
 *     {
 *       "type": "vector",
 *       "path": "embedding",
 *       "numDimensions": 1536,
 *       "similarity": "cosine",
 *       "autoEmbed": { "model": "voyage-3-large" }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>Requires MongoDB Atlas or MongoDB >= 7.0 with Atlas Search enabled.
 */
@RegisterPlugin(
    name = "vectorSearchIndexCreateInterceptor",
    description = "Creates Atlas Vector Search indexes via PUT /_indexes/{name} with type:vectorSearch",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    requiresContent = true,
    priority = Integer.MIN_VALUE
)
public class VectorSearchIndexCreateInterceptor implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorSearchIndexCreateInterceptor.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isIndex() || !request.isPut()) {
            return false;
        }
        var content = request.getContent();
        if (content == null || !content.isDocument()) {
            return false;
        }
        var type = content.asDocument().get("type");
        return type != null && type.isString() && "vectorSearch".equals(type.asString().getValue());
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var content   = request.getContent().asDocument();
        var indexName = request.getIndexId();

        var _fields = content.get("fields");
        if (_fields == null || !_fields.isArray()) {
            response.setInError(HttpStatus.SC_BAD_REQUEST,
                "vectorSearch index definition must include a 'fields' array");
            return;
        }

        var definition = new BsonDocument("fields", _fields.asArray());

        try {
            var model = new SearchIndexModel(
                indexName,
                Document.parse(definition.toJson()),
                SearchIndexType.vectorSearch());

            mclient.getDatabase(request.getDBName())
                .getCollection(request.getCollectionName(), BsonDocument.class)
                .createSearchIndexes(java.util.List.of(model));

            LOGGER.info("Created vector search index '{}' on {}/{}", indexName,
                request.getDBName(), request.getCollectionName());
        } catch (Exception e) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "error creating vector search index", e);
            return;
        }

        // Signal success and prevent the standard PutIndexHandler from running.
        // Setting request.setInError(true) causes all subsequent pipelined handlers
        // to skip their logic while leaving response.isInError() = false so that
        // the response is NOT formatted as an error document.
        response.setStatusCode(HttpStatus.SC_CREATED);
        request.setInError(true);
    }
}
