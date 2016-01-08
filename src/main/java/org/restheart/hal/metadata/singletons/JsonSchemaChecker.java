/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.hal.metadata.singletons;

import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.Objects;
import java.util.Set;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.schema.JsonSchemaCacheSingleton;
import org.restheart.handlers.schema.JsonSchemaNotFoundException;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonSchemaChecker implements Checker {
    public static final String SCHEMA_STORE_DB_PROPERTY = "db";
    public static final String SCHEMA_ID_PROPERTY = "id";

    static final Logger LOGGER = LoggerFactory.getLogger(JsonSchemaChecker.class);

    @Override
    public boolean check(HttpServerExchange exchange, RequestContext context, DBObject args) {
        boolean patching = context.getMethod() == RequestContext.METHOD.PATCH;

        Objects.requireNonNull(args, "missing metadata property 'args'");

        Object _schemaStoreDb = args.get(SCHEMA_STORE_DB_PROPERTY);
        String schemaStore;

        Object schemaId = args.get(SCHEMA_ID_PROPERTY);

        Objects.requireNonNull(schemaId, "missing property '" + SCHEMA_ID_PROPERTY + "' in metadata property 'args'");

        if (_schemaStoreDb == null) {
            // if not specified assume the current db as the schema store db
            schemaStore = context.getDBName();
        } else if (_schemaStoreDb instanceof String) {
            schemaStore = (String) _schemaStoreDb;
        } else {
            throw new IllegalArgumentException("property " + SCHEMA_STORE_DB_PROPERTY + " in metadata 'args' must be a a string");
        }

        try {
            URLUtils.checkId(schemaId);
        } catch (UnsupportedDocumentIdException ex) {
            throw new IllegalArgumentException("schema 'id' is not a valid id", ex);
        }

        Schema theschema;

        if (patching) {
            LOGGER.debug("@@@@ PATCHING");

            DBObject c = context.getContent();

            Set<String> ids = c.keySet();

            LOGGER.debug("@@@@ ids: {}", ids);

            theschema = getPatchSchema(schemaStore, schemaId, ids);
        } else {
            try {
                theschema = JsonSchemaCacheSingleton
                        .getInstance()
                        .get(exchange.getRequestURL(), schemaStore, schemaId);
            } catch (JsonSchemaNotFoundException ex) {
                context.addWarning(ex.getMessage());
                return false;
            }

            if (Objects.isNull(theschema)) {
                throw new IllegalArgumentException("cannot validate, schema "
                        + schemaStore
                        + "/"
                        + RequestContext._SCHEMAS
                        + "/" + schemaId.toString() + " not found");
            }
        }

        String content = context.getContent() == null
                ? "{}"
                : context.getContent().toString();

        try {
            theschema.validate(
                    new JSONObject(content));
        } catch (ValidationException ve) {
            context.addWarning(ve.getMessage());
            ve.getCausingExceptions().stream()
                    .map(ValidationException::getMessage)
                    .forEach(context::addWarning);

            return false;
        }

        return true;
    }

    /**
     *
     * given keys as ["a", "b.c", "array.3.id"] returns a schema as follows
     *
     * { "$schema": "http://json-schema.org/draft-04/schema#", "id":
     * "schema://schema_store/temp#", "properties": { "a": { "$ref":
     * "schemaid#/a" }, "b.c": { "$ref": "schemaid#/b/c" }, "array.3.id": {
     * "$ref": "schemaid#/array/3/id" } }
     *
     * @return the schema for patch requests
     */
    private Schema getPatchSchema(String schemaStore, Object schemaId, Set<String> keys) {
        JSONObject schema = new JSONObject();

        schema.put("$schema", "http://json-schema.org/draft-04/schema#");
        schema.put("id", "schema://" + schemaStore + "/" + schemaId + "#");

        JSONObject properties = new JSONObject();

        keys.stream().forEach(key -> {
            properties.put(key, new JSONObject().put("$ref", schemaId + "#/properties/" + key.replaceAll("\\.", "/")));
        });

        schema.put("properties", properties);

        LOGGER.debug("patch schema {}", schema);

        return SchemaLoader.load(schema, new SchemaStoreClient());
    }
}
