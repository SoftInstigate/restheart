/*-
 * ========================LICENSE_START=================================
 * restheart-commons
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
package org.restheart.plugins.schema;

import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * Interface to the JSON Schema store.
 * <p>
 * Exposed as a {@code Provider} so that any plugin can validate documents
 * against schemas stored in the {@code _schemas} collection, without depending
 * on {@code restheart-mongodb} or leaking the underlying validation library.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface JsonSchemas {

    /**
     * Validates a document against a JSON schema loaded from the schema store.
     *
     * @param doc           the document to validate
     * @param schemaStoreDb the database containing the {@code _schemas} collection
     * @param schemaId      the {@code _id} of the schema document
     * @throws SchemaValidationException if the document does not validate
     * @throws JsonSchemaNotFoundException if the schema is not found in the store
     */
    void validate(BsonDocument doc, String schemaStoreDb, BsonValue schemaId)
            throws SchemaValidationException, JsonSchemaNotFoundException;

    /**
     * Returns the raw JSON schema as a string.
     *
     * @param schemaStoreDb the database containing the {@code _schemas} collection
     * @param schemaId      the {@code _id} of the schema document
     * @return the schema document as a JSON string
     * @throws JsonSchemaNotFoundException if the schema is not found in the store
     */
    String get(String schemaStoreDb, BsonValue schemaId)
            throws JsonSchemaNotFoundException;
}
