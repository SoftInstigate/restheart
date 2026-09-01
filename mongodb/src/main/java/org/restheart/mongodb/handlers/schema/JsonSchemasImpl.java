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
package org.restheart.mongodb.handlers.schema;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.restheart.plugins.schema.JsonSchemaNotFoundException;
import org.restheart.plugins.schema.JsonSchemas;
import org.restheart.plugins.schema.SchemaValidationException;
import org.restheart.utils.BsonUtils;

/**
 * Implementation of {@link JsonSchemas} that delegates to
 * {@link JsonSchemaCacheSingleton} for caching and loading schemas.
 * <p>
 * Everit types never cross this class boundary — the API exposes only
 * {@link String} (raw JSON) and {@link SchemaValidationException}.
 */
public class JsonSchemasImpl implements JsonSchemas {

    // Lazy: MongoServiceConfiguration is not ready at provider-registration time
    private JsonSchemaCacheSingleton cache() {
        return JsonSchemaCacheSingleton.getInstance();
    }

    @Override
    public void validate(BsonDocument doc, String schemaStoreDb, BsonValue schemaId)
            throws SchemaValidationException, JsonSchemaNotFoundException {
        validate(schema(schemaStoreDb, schemaId), doc);
    }

    @Override
    public void validate(List<BsonDocument> docs, String schemaStoreDb, BsonValue schemaId)
            throws SchemaValidationException, JsonSchemaNotFoundException {
        // resolve the schema once: with schema-cache-enabled=false this saves
        // one mongo read plus one SchemaLoader.load() per document
        var schema = schema(schemaStoreDb, schemaId);

        for (var doc : docs) {
            validate(schema, doc);
        }
    }

    private org.everit.json.schema.Schema schema(String schemaStoreDb, BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        try {
            return cache().get(schemaStoreDb, schemaId);
        } catch (org.restheart.mongodb.handlers.schema.JsonSchemaNotFoundException ex) {
            throw new JsonSchemaNotFoundException(ex.getMessage(), ex);
        }
    }

    private void validate(org.everit.json.schema.Schema schema, BsonDocument doc)
            throws SchemaValidationException {
        // the document is always rendered with the default json mode: validation
        // must not depend on the jsonMode of the request that triggered it
        try {
            schema.validate(new JSONObject(BsonUtils.toJson(doc)));
        } catch (ValidationException ve) {
            var errors = new ArrayList<String>();

            errors.add(ve.getMessage().replaceAll("#: ", ""));

            ve.getCausingExceptions().stream()
                    .map(ValidationException::getMessage)
                    .forEach(errors::add);

            var errMsgBuilder = new StringBuilder();

            errors.stream()
                    .map(e -> e.replaceAll("#: ", ""))
                    .forEachOrdered(e -> errMsgBuilder.append(e).append(", "));

            var errMsg = errMsgBuilder.toString();

            if (errMsg.length() > 2
                    && ", ".equals(errMsg.substring(errMsg.length() - 2, errMsg.length()))) {
                errMsg = errMsg.substring(0, errMsg.length() - 2);
            }

            throw new SchemaValidationException(
                    "Document violates schema: " + errMsg,
                    errors,
                    ve);
        }
    }

    @Override
    public String get(String schemaStoreDb, BsonValue schemaId)
            throws JsonSchemaNotFoundException {
        try {
            var raw = cache().getRaw(schemaStoreDb, schemaId);
            return raw.toJson();
        } catch (org.restheart.mongodb.handlers.schema.JsonSchemaNotFoundException ex) {
            throw new JsonSchemaNotFoundException(ex.getMessage(), ex);
        }
    }
}
