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
import org.bson.Document;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONException;
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
 * checks documents according to the specified json-schema
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonSchemaChecker implements Checker {
    public static final String SCHEMA_STORE_DB_PROPERTY = "schemaStoreDb";
    public static final String SCHEMA_ID_PROPERTY = "schemaId";
    public static final String FAIL_IF_NOT_SUPPORTED_PROPERTY = "failIfNotSupported";

    static final Logger LOGGER = LoggerFactory.getLogger(JsonSchemaChecker.class);

    @Override
    public boolean check(
            HttpServerExchange exchange,
            RequestContext context,
            BasicDBObject contentToCheck,
            DBObject args) {
        Objects.requireNonNull(args, "missing metadata property 'args'");

        Object _schemaStoreDb = args.get(SCHEMA_STORE_DB_PROPERTY);
        String schemaStoreDb;

        Object schemaId = args.get(SCHEMA_ID_PROPERTY);

        Objects.requireNonNull(schemaId, "missing property '" + SCHEMA_ID_PROPERTY + "' in metadata property 'args'");

        if (_schemaStoreDb == null) {
            // if not specified assume the current db as the schema store db
            schemaStoreDb = context.getDBName();
        } else if (_schemaStoreDb instanceof String) {
            schemaStoreDb = (String) _schemaStoreDb;
        } else {
            throw new IllegalArgumentException("property " + SCHEMA_STORE_DB_PROPERTY + " in metadata 'args' must be a string");
        }

        try {
            URLUtils.checkId(schemaId);
        } catch (UnsupportedDocumentIdException ex) {
            throw new IllegalArgumentException("schema 'id' is not a valid id", ex);
        }

        Schema theschema;

        try {
            theschema = JsonSchemaCacheSingleton
                    .getInstance()
                    .get(schemaStoreDb, schemaId);
        } catch (JsonSchemaNotFoundException ex) {
            context.addWarning(ex.getMessage());
            return false;
        }

        if (Objects.isNull(theschema)) {
            throw new IllegalArgumentException("cannot validate, schema "
                    + schemaStoreDb
                    + "/"
                    + RequestContext._SCHEMAS
                    + "/" + schemaId.toString() + " not found");
        }

        Objects.requireNonNull(context.getDbOperationResult());

        Document data = context.getDbOperationResult().getNewData();

        String _data = data == null
                ? "{}"
                : data.toJson();

        LOGGER.debug(_data);

        try {
            theschema.validate(
                    new JSONObject(_data));
        } catch (JSONException je) {
            context.addWarning(je.getMessage());

            return false;
        } catch (ValidationException ve) {
            context.addWarning(ve.getMessage());
            ve.getCausingExceptions().stream()
                    .map(ValidationException::getMessage)
                    .forEach(context::addWarning);

            return false;
        }

        return true;
    }

    @Override
    public PHASE getPhase() {
        return PHASE.AFTER_WRITE;
    }

    @Override
    public boolean shouldCheckFailIfNotSupported(DBObject args) {
        Object _failIfNotSupported = args.get(FAIL_IF_NOT_SUPPORTED_PROPERTY);

        if (_failIfNotSupported == null) {
            return true;
        } else if (_failIfNotSupported instanceof Boolean) {
            return (Boolean) _failIfNotSupported;
        } else {
            throw new IllegalArgumentException("property " + FAIL_IF_NOT_SUPPORTED_PROPERTY + " in metadata 'args' must be boolean");
        }
    }

    @Override
    public boolean doesSupportRequests(RequestContext context) {
        return !CheckersUtils.isBulkRequest(context);
    }
}
