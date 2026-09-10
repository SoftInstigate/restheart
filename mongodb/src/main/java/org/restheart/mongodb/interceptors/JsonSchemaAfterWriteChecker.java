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
package org.restheart.mongodb.interceptors;

import static com.mongodb.client.model.Filters.in;

import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.RHMongoClients;
import org.restheart.mongodb.db.BulkOperationResult;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.schema.JsonSchemas;

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
public class JsonSchemaAfterWriteChecker extends JsonSchemaBeforeWriteChecker {

    @Inject("json-schemas")
    private JsonSchemas jsonSchemas;

    @Override
    protected JsonSchemas jsonSchemas() {
        return jsonSchemas;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        super.handle(request, response);

        var mclient = RHMongoClients.mclient();

        if (mclient == null) {
            throw new IllegalStateException("mclient not availabe");
        }

        if (request.isInError()) {
            response.rollback(mclient);
        }
    }

    /**
     * A bulk {@code PATCH} is included only when it ran in a transaction that collected the ids it
     * touched — {@code jsonSchemaAfterWriteTxn} asks for that transaction, and only asks where one
     * is available. Without the ids there is nothing to re-read and nothing to check, and letting
     * the request through unchecked is the behaviour {@code jsonSchemaBeforeWrite} already decides
     * on, with {@code skipNotSupported}.
     */
    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && request.getCollectionProps() != null
                && request.isPatch()
                // isWriteDocument() is false for a bulk PATCH, whose resource type is
                // BULK_DOCUMENTS; there what stands in for it is having the ids to re-read
                && (request.isBulkDocuments()
                        ? patchedIds(response) != null
                        : request.isWriteDocument())
                && request.getCollectionProps().containsKey("jsonSchema")
                && request.getCollectionProps().get("jsonSchema").isDocument()
                && (response.getDbOperationResult() != null && response.getDbOperationResult().getHttpCode() < 300);
    }

    /** The ids a bulk patch touched, or null when the write did not collect them. */
    private static List<BsonValue> patchedIds(MongoResponse response) {
        return response.getDbOperationResult() instanceof BulkOperationResult bulk
                ? bulk.getPatchedIds()
                : null;
    }

    @Override
    List<BsonDocument> documentsToCheck(MongoRequest request, MongoResponse response) {
        if (request.isBulkDocuments()) {
            return patchedDocuments(request, patchedIds(response));
        }

        var content = response.getDbOperationResult().getNewData() == null
                ? new BsonDocument()
                : response.getDbOperationResult().getNewData();

        return List.of(content);
    }

    /**
     * Re-reads the documents a bulk patch modified, as they now are.
     *
     * <p>Read in the request's own session, so it sees the update that is not committed yet — the
     * whole point, since what has to be validated is the outcome of the patch and not the state
     * before it. By id rather than by the request's filter: an update can push a document out of
     * the filter that selected it, and that document would then escape the check.
     */
    private List<BsonDocument> patchedDocuments(MongoRequest request, List<BsonValue> ids) {
        final var docs = new ArrayList<BsonDocument>();

        if (ids.isEmpty()) {
            return docs;
        }

        RHMongoClients.mclient()
                .getDatabase(request.getDBName())
                .getCollection(request.getCollectionName(), BsonDocument.class)
                .find(request.getClientSession(), in("_id", ids))
                .into(docs);

        return docs;
    }
}
