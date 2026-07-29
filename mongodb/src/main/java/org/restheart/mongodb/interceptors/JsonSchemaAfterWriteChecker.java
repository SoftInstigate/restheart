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

import java.util.List;
import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.RHMongoClients;
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

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
            && request.getCollectionProps() != null
            && (request.isPatch() && !request.isBulkDocuments())
            && request.isWriteDocument()
            && request.getCollectionProps() != null
            && request.getCollectionProps().containsKey("jsonSchema")
            && request.getCollectionProps().get("jsonSchema").isDocument()
            && (response.getDbOperationResult() != null && response.getDbOperationResult().getHttpCode() < 300);
    }

    @Override
    List<BsonDocument> documentsToCheck(MongoRequest request, MongoResponse response) {
        var content = response.getDbOperationResult().getNewData() == null
            ? new BsonDocument()
            : response.getDbOperationResult().getNewData();

        return java.util.List.of(content);
    }
}
