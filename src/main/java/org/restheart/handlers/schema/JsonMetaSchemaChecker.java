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
package org.restheart.handlers.schema;

import io.undertow.server.HttpServerExchange;
import java.io.InputStream;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.Checker;
import org.restheart.plugins.checkers.CheckersUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * checks the schema of the schemas using json metaschema
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonMetaSchemaChecker implements Checker {

    static final String JSON_METASCHEMA_FILENAME = "json-schema-draft-v4.json";

    static final Logger LOGGER
            = LoggerFactory.getLogger(JsonMetaSchemaChecker.class);

    private static Schema schema;

    static {
        try {
            InputStream jsonMetaschemaIS = JsonMetaSchemaChecker.class
                    .getClassLoader()
                    .getResourceAsStream(JSON_METASCHEMA_FILENAME);

            JSONObject rawSchema
                    = new JSONObject(new JSONTokener(jsonMetaschemaIS));

            schema = SchemaLoader.load(rawSchema);
        } catch (JSONException ex) {
            LOGGER.error("error initializing", ex);
        }
    }

    @Override
    public boolean check(
            HttpServerExchange exchange,
            RequestContext context,
            BsonDocument contentToCheck,
            BsonValue args) {
        if (contentToCheck == null) {
            return false;
        }

        try {
            schema.validate(new JSONObject(contentToCheck.toString()));
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
    public PHASE getPhase(RequestContext context) {
        if (context.getMethod() == RequestContext.METHOD.PATCH
                || CheckersUtils.doesRequestUsesDotNotation(
                        context.getContent())
                || CheckersUtils.doesRequestUsesUpdateOperators(
                        context.getContent())) {
            return PHASE.AFTER_WRITE;
        } else {
            return PHASE.BEFORE_WRITE;
        }
    }

    @Override
    public boolean doesSupportRequests(RequestContext context) {
        return !(CheckersUtils.isBulkRequest(context)
                && getPhase(context) == PHASE.AFTER_WRITE);
    }
}
