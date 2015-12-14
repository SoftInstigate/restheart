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
import java.io.IOException;
import java.io.InputStream;
import org.bson.types.ObjectId;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonSchemaStore implements Checker, Transformer {
    static final Logger LOGGER = LoggerFactory.getLogger(JsonSchemaStore.class);

    private static Schema schema;

    static {
        try (InputStream inputStream = JsonSchemaStore.class.getClassLoader().getResourceAsStream("json-schema-draft-v4.json")) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            schema = SchemaLoader.load(rawSchema);
        } catch (IOException ioe) {
            LOGGER.error("error initializing {}", ioe);
        }
    }

    @Override
    public boolean check(HttpServerExchange exchange, RequestContext context, DBObject args) {
        Object schemaId;

        if (context.getMethod() == RequestContext.METHOD.PATCH) {
            context.addWarning("patching a schema is not allowed");
            return false;
        } else if (context.getMethod() == RequestContext.METHOD.POST) {
            if (context.getContent().get("_id") == null) {
                schemaId = new ObjectId();
                context.getContent().put("id", schemaId);
            } else {
                schemaId = context.getContent().get("_id");
            }
        } else {
            schemaId = context.getDocumentId();
        }

        // always overwrite the id with the URL
        context.getContent().put("id", URLUtils.getReferenceLink(context, exchange.getRequestURL(), schemaId));

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

    @Override
    public void tranform(HttpServerExchange exchange, RequestContext context, DBObject contentToTransform, DBObject args) {
        if (context.getMethod() == RequestContext.METHOD.GET) {
            contentToTransform.put("$schema", "http://json-schema.org/draft-04/schema#");
        }
    }
}
