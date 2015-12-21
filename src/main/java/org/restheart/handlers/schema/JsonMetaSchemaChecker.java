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

import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.restheart.hal.metadata.singletons.Checker;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.ResourcesExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * checks the schema of the schemas using json metaschema
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonMetaSchemaChecker implements Checker {
    static final Logger LOGGER = LoggerFactory.getLogger(JsonMetaSchemaChecker.class);

    private static Schema schema;

    static {
        try  {
            File file = ResourcesExtractor.extract("json-schema-draft-v4.json");

            InputStream inputStream = new FileInputStream(file);
            
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            schema = SchemaLoader.load(rawSchema);
        } catch (IOException | URISyntaxException ex) {
            LOGGER.error("error initializing {}", ex);
        }
    }

    @Override
    public boolean check(HttpServerExchange exchange, RequestContext context, DBObject args) {
        try {
            schema.validate(new JSONObject(context.getContent().toString()));
        } catch (ValidationException ve) {
            context.addWarning(ve.getMessage());
            ve.getCausingExceptions().stream()
                    .map(ValidationException::getMessage)
                    .forEach(context::addWarning);

            return false;
        }

        return true;
    }
}
