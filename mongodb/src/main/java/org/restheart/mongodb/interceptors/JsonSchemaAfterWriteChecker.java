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
package org.restheart.mongodb.interceptors;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.util.Headers;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.json.JSONObject;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.db.DAOUtils;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;

/**
 *
 * Checks documents according to the specified JSON schema
 *
 * This intercetor is able to check PATCH requests (excluding bulk PATCH). Other
 * requests are checked by jsonSchemaBeforeWrite
 *
 * It checks the request content against the JSON schema specified by the
 * 'jsonSchema' collection metadata:
 * <br><br>
 * { "jsonSchema": { "schemaId": &lt;schemaId&gt; "schemaStoreDb":
 * &lt;schemaStoreDb&gt; } }
 * <br><br>
 * schemaStoreDb is optional, default value is same db
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "jsonSchemaAfterWrite",
        description = "Checks the request content against the JSON schema specified by the 'jsonSchema' collection metadata",
        interceptPoint = InterceptPoint.RESPONSE)
@SuppressWarnings("deprecation")
public class JsonSchemaAfterWriteChecker extends JsonSchemaBeforeWriteChecker {
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        super.handle(request, response);

        if (request.isInError()) {
            rollback(request, response);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.getCollectionProps() != null
                && (request.isPatch() && !request.isBulkDocuments())
                && request.getCollectionProps() != null
                && request.getCollectionProps()
                        .containsKey("jsonSchema")
                && request.getCollectionProps()
                        .get("jsonSchema")
                        .isDocument()
                && (response.getDbOperationResult() != null
                && response.getDbOperationResult().getHttpCode() < 300);
    }

    String documentToCheck(MongoRequest request, MongoResponse response) {
        return response.getDbOperationResult().getNewData() == null
                ? "{}"
                : response.getDbOperationResult().getNewData().toJson();
    }

    @Override
    List<JSONObject> documentsToCheck(MongoRequest request, MongoResponse response) {
        var ret = new ArrayList<JSONObject>();

        var content = response.getDbOperationResult().getNewData() == null
                ? new BsonDocument()
                : response.getDbOperationResult().getNewData();

        ret.add(new JSONObject(content.asDocument().toJson()));

        return ret;
    }

    private void rollback(MongoRequest request, MongoResponse response)
            throws Exception {
        var exchange = request.getExchange();

        // restore old data
        MongoClient client = MongoClientSingleton
                .getInstance()
                .getClient();

        MongoDatabase mdb = client
                .getDatabase(request.getDBName());

        MongoCollection<BsonDocument> coll = mdb
                .getCollection(
                        request.getCollectionName(),
                        BsonDocument.class);

        BsonDocument oldData = response
                .getDbOperationResult()
                .getOldData();

        Object newEtag = response.getDbOperationResult().getEtag();

        if (oldData != null) {
            // document was updated, restore old one
            DAOUtils.restoreDocument(
                    request.getClientSession(),
                    coll,
                    oldData.get("_id"),
                    request.getShardKey(),
                    oldData,
                    newEtag,
                    "_etag");

            // add to response old etag
            if (oldData.get("$set") != null
                    && oldData.get("$set").isDocument()
                    && oldData.get("$set")
                            .asDocument()
                            .get("_etag") != null) {
                exchange.getResponseHeaders().put(Headers.ETAG,
                        oldData.get("$set")
                                .asDocument()
                                .get("_etag")
                                .asObjectId()
                                .getValue()
                                .toString());
            } else {
                exchange.getResponseHeaders().remove(Headers.ETAG);
            }

        } else {
            // document was created, delete it
            Object newId = response.getDbOperationResult()
                    .getNewData().get("_id");

            coll.deleteOne(and(eq("_id", newId), eq("_etag", newEtag)));
        }
    }
}
