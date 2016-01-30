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

import com.google.common.collect.Lists;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.bson.types.ObjectId;
import org.restheart.hal.metadata.singletons.Transformer;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonSchemaTransformer implements Transformer {
    static final Logger LOGGER = LoggerFactory
            .getLogger(JsonSchemaTransformer.class);

    @Override
    public void tranform(HttpServerExchange exchange, 
            RequestContext context, 
            final DBObject contentToTransform, DBObject args) {
        if (context.getType() == RequestContext.TYPE.SCHEMA) {
            if (context.getMethod() == RequestContext.METHOD.GET) {
                unescapeSchema(context.getResponseContent());
            } else if (context.getMethod() == RequestContext.METHOD.PUT
                    || context.getMethod() == RequestContext.METHOD.PATCH) {

                // generate id as specs mandates
                SchemaStoreURL uri = new SchemaStoreURL(
                        context.getDBName(),
                        context.getDocumentId());

                context.getContent().put("id", uri.toString());

                // escape all $ prefixed keys
                escapeSchema(contentToTransform);

                // add (overwrite) $schema field
                contentToTransform.put("_$schema",
                        "http://json-schema.org/draft-04/schema#");
            }
        } else if (context.getType() == RequestContext.TYPE.SCHEMA_STORE) {
            if (context.getMethod() == RequestContext.METHOD.POST) {
                // generate id as specs mandates
                Object schemaId;

                if (context.getContent().get("_id") == null) {
                    schemaId = new ObjectId();
                    context.getContent().put("id", schemaId);
                } else {
                    schemaId = context.getContent().get("_id");
                }

                SchemaStoreURL uri = new SchemaStoreURL(context.getDBName(), schemaId);

                context.getContent().put("id", uri.toString());

                // escape all $ prefixed keys
                escapeSchema(contentToTransform);

                // add (overwrite) $schema field
                contentToTransform.put("_$schema",
                        "http://json-schema.org/draft-04/schema#");
            } else if (context.getMethod() == RequestContext.METHOD.GET) {
                // apply transformation on embedded schemas

                BasicDBObject _embedded = (BasicDBObject) context
                        .getResponseContent().get("_embedded");

                if (_embedded != null) {
                    // execute the logic on children documents
                    BasicDBList docs = (BasicDBList) _embedded.get("rh:schema");

                    if (docs != null) {
                        docs.keySet().stream().map((k) -> (DBObject) 
                                docs.get(k))
                                .forEach((doc) -> {
                                    unescapeSchema(doc);
                                });
                    }
                }
            }
        }
    }

    public static void escapeSchema(DBObject schema) {
        DBObject escaped = (DBObject) JsonUtils.escapeKeys(schema);

        List<String> keys = Lists.newArrayList(schema.keySet().iterator());

        keys.stream().forEach(f -> schema.removeField(f));

        schema.putAll(escaped);
    }

    public static void unescapeSchema(DBObject schema) {
        DBObject unescaped = (DBObject) JsonUtils.unescapeKeys(schema);

        List<String> keys = Lists.newArrayList(schema.keySet().iterator());

        keys.stream().forEach(f -> schema.removeField(f));

        schema.putAll(unescaped);
    }
}
