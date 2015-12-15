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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.Objects;
import org.bson.types.ObjectId;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.restheart.db.Database;
import org.restheart.db.DbsDAO;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonSchemaChecker implements Checker {
    private final Database dbsDAO;

    public static final String SCHEMA_PROPERTY_NAME = "schema";
    public static final String SCHEMA_DB_PROPERTY_NAME = "db";
    public static final String SCHEMA_COLL_PROPERTY_NAME = "coll";
    public static final String SCHEMA_ID_PROPERTY_NAME = "id";

    static final Logger LOGGER = LoggerFactory.getLogger(JsonSchemaChecker.class);

    public JsonSchemaChecker() {
        this.dbsDAO = new DbsDAO();
    }

    @Override
    public boolean check(HttpServerExchange exchange, RequestContext context, DBObject args) {
        boolean patching = context.getMethod() == RequestContext.METHOD.PATCH;

        if (patching) {
            throw new RuntimeException("json schema checking on PATCH requests not yet implemented");
        }

        Objects.requireNonNull(args, "missing metadata property 'args'");

        Object _schema = args.get(SCHEMA_PROPERTY_NAME);

        Objects.requireNonNull(_schema, "missing '" + SCHEMA_PROPERTY_NAME + "' in metadata property 'args'");

        if (!(_schema instanceof BasicDBObject)) {
            throw new IllegalArgumentException("wrong 'schema' in metadata property 'args', it must be an object");
        }

        BasicDBObject schema = (BasicDBObject) _schema;

        Object _coll = schema.get(SCHEMA_COLL_PROPERTY_NAME);
        String coll;
        Object _db = schema.get(SCHEMA_DB_PROPERTY_NAME);
        String db;

        Object schemaId = schema.get(SCHEMA_ID_PROPERTY_NAME);

        Objects.requireNonNull(_db, "missing '" + SCHEMA_DB_PROPERTY_NAME + "' in metadata property 'args'");
        Objects.requireNonNull(_coll, "missing '" + SCHEMA_COLL_PROPERTY_NAME + "' in metadata property 'args'");
        Objects.requireNonNull(schemaId, "missing property '" + SCHEMA_ID_PROPERTY_NAME + "' in metadata property 'args'");

        if (_db instanceof String) {
            db = (String) _db;
        } else {
            throw new IllegalArgumentException("property " + SCHEMA_COLL_PROPERTY_NAME + " in metadata 'args' must be a a string");
        }

        if (_coll instanceof String) {
            coll = (String) _coll;
        } else {
            throw new IllegalArgumentException("property " + SCHEMA_COLL_PROPERTY_NAME + " in metadata 'args' must be a a string");
        }

        if (!(schemaId instanceof String || schemaId instanceof ObjectId)) {
            throw new IllegalArgumentException("wrong schema 'id': it must be either a string or an ObjectId");
        }

        try {
            Schema theschema = getSchema(db, coll, schemaId);

            if (Objects.isNull(theschema)) {
                throw new IllegalArgumentException("cannot validate, schema "  + db + "/" + coll + "/" + schemaId.toString() + " not found");
            }

            theschema.validate(
                    new JSONObject(context.getContent().toString()));
        } catch (ValidationException ve) {
            context.addWarning(ve.getMessage());
            ve.getCausingExceptions().stream()
                    .map(ValidationException::getMessage)
                    .forEach(context::addWarning);

            return false;
        }

        return true;
    }

    private Schema getSchema(String schemaDb, String schemaCollection, Object schemaId) {
        DBObject document = dbsDAO.getCollection(schemaDb, schemaCollection).findOne(schemaId);

        if (Objects.isNull(document)) {
            return null;
        }

        return SchemaLoader.load(new JSONObject(document.toMap()));
    }
}
