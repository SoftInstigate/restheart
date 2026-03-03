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
 * <p>autoEmbed example. Requires MongoDB Atlas (Voyage AI key configured in the Atlas project
 * Integrations) or MongoDB CE 8.2+ with mongot (Voyage AI key passed to mongot via its
 * configuration). Without a valid Voyage AI key the index will stay in PENDING state indefinitely.
 * <pre>{@code
 * PUT /mydb/mycollection/_indexes/myAutoEmbedIndex
 * {
 *   "type": "vectorSearch",
 *   "fields": [
 *     {
 *       "type": "autoEmbed",
 *       "path": "text",
 *       "modality": "text",
 *       "model": "voyage-3-large"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>To query an autoEmbed index, use {@code queryString} in {@code $vectorSearch} instead of
 * {@code queryVector} — the embedding for the query text is generated automatically by mongot:
 * <pre>{@code
 * PATCH /mydb/mycollection
 * {
 *   "aggrs": [
 *     {
 *       "type": "pipeline",
 *       "uri": "semantic_search",
 *       "stages": [
 *         {
 *           "_$vectorSearch": {
 *             "index": "myAutoEmbedIndex",
 *             "path": "text",
 *             "queryString": { "$var": "q" },
 *             "numCandidates": 100,
 *             "limit": 10
 *           }
 *         }
 *       ]
 *     }
 *   ]
 * }
 * }</pre>
 * Then query via: {@code GET /mydb/mycollection/_aggrs/semantic_search?avars={"q":"your query text"}}
 *
 * <p>{@code autoEmbed} and {@code queryString} require MongoDB Atlas or MongoDB CE 8.2+ with
 * mongot and a Voyage AI API key. Without the key, the index stays PENDING.
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
        } catch (com.mongodb.MongoCommandException mce) {
            var mongoMsg = mce.getErrorMessage();
            var hasAutoEmbed = _fields.asArray().stream()
                .filter(org.bson.BsonValue::isDocument)
                .map(v -> v.asDocument())
                .anyMatch(f -> "autoEmbed".equals(f.getString("type", new org.bson.BsonString("")).getValue()));
            String msg;
            if (hasAutoEmbed && mongoMsg != null && mongoMsg.contains("autoEmbed")) {
                msg = "autoEmbed requires a Voyage AI API key: on Atlas configure it in the project "
                    + "Integrations; on CE 8.2+ pass it to mongot via its configuration. "
                    + "Without a valid key the index stays in PENDING state indefinitely. "
                    + "Query the index using 'queryString' in $vectorSearch (embedding generated by mongot). "
                    + "See: https://www.mongodb.com/docs/atlas/atlas-vector-search/crud-embeddings/create-embeddings-automatic/";
            } else {
                msg = "error creating vector search index: [" + mce.getErrorCode() + "] "
                    + mce.getErrorCodeName() + " - " + mongoMsg;
            }
            LOGGER.warn(msg);
            response.setInError(HttpStatus.SC_BAD_REQUEST, msg);
            return;
        } catch (Exception e) {
            LOGGER.warn("Error creating vector search index '{}' on {}/{}: {}",
                indexName, request.getDBName(), request.getCollectionName(), e.getMessage(), e);
            response.setInError(HttpStatus.SC_BAD_REQUEST,
                "error creating vector search index: " + e.getMessage());
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
