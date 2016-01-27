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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.io.InputStream;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.restheart.hal.metadata.singletons.Checker;
import org.restheart.hal.metadata.singletons.CheckersUtils;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * checks the schema of the schemas using json metaschema
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonMetaSchemaChecker implements Checker {
    static final String JSON_METASCHEMA_FILENAME = "json-schema-draft-v4.json";
    
    static final Logger LOGGER = LoggerFactory.getLogger(JsonMetaSchemaChecker.class);

    private static Schema schema;

    static {
        try  {
            InputStream jsonMetaschemaIS = JsonMetaSchemaChecker.class
                    .getClassLoader()
                    .getResourceAsStream(JSON_METASCHEMA_FILENAME);

            JSONObject rawSchema = 
                    new JSONObject(new JSONTokener(jsonMetaschemaIS));
            
            schema = SchemaLoader.load(rawSchema);
        } catch (Throwable ex) {
            LOGGER.error("error initializing", ex);
        }
    }

    @Override
    public boolean check(
            HttpServerExchange exchange, 
            RequestContext context, 
            BasicDBObject contentToCheck, 
            DBObject args) {
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
    public PHASE getPhase() {
        return PHASE.BEFORE_WRITE;
    }

    @Override
    public boolean shouldCheckFailIfNotSupported(DBObject args) {
        return true;
    }

    @Override
    public boolean doesSupportRequests(RequestContext context) {
        return !CheckersUtils.isBulkRequest(context);
    }

}
