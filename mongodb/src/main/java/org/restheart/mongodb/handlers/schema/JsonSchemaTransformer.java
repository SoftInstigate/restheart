/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

import com.google.common.collect.Lists;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.plugins.mongodb.Transformer;
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

    private static final BsonString $SCHEMA
            = new BsonString("http://json-schema.org/draft-04/schema#");

    /**
     *
     * @param schema
     */
    public static void escapeSchema(BsonDocument schema) {
        BsonValue escaped = JsonUtils.escapeKeys(schema, false);

        if (escaped.isDocument()) {
            List<String> keys = Lists.newArrayList(schema.keySet().iterator());

            keys.stream().forEach(f -> schema.remove(f));

            schema.putAll(escaped.asDocument());
        }
    }

    /**
     *
     * @param schema
     */
    public static void unescapeSchema(BsonDocument schema) {
        BsonValue unescaped = JsonUtils.unescapeKeys(schema);

        if (unescaped != null && unescaped.isDocument()) {
            List<String> keys = Lists.newArrayList(schema.keySet().iterator());

            keys.stream().forEach(f -> schema.remove(f));

            schema.putAll(unescaped.asDocument());
        }
    }

    @Override
    public void transform(HttpServerExchange exchange,
            RequestContext context,
            final BsonValue contentToTransform,
            BsonValue args) {
        if (context.isInError()) {
            return;
        }
        
        BsonDocument _contentToTransform
                = contentToTransform == null
                        ? null
                        : contentToTransform.asDocument();

        if (context.isSchema()) {
            if (context.isGet()) {
                unescapeSchema(_contentToTransform);
            } else if (context.isPut() || context.isPatch()) {
                BsonDocument content;

                if (context.getContent() != null) {
                    content = context.getContent().asDocument();
                } else {
                    content = new BsonDocument();
                }

                // generate id as specs mandates
                SchemaStoreURL uri = new SchemaStoreURL(
                        context.getDBName(),
                        context.getDocumentId());

                content.put("id", new BsonString(uri.toString()));

                // escape all $ prefixed keys
                escapeSchema(_contentToTransform);

                // add (overwrite) $schema field
                if (null != _contentToTransform) {
                    _contentToTransform.put("_$schema", $SCHEMA);
                }
            }
        } else if (context.isSchemaStore()) {
            if (context.isPost()) {
                BsonDocument content;

                if (context.getContent() != null) {
                    content = context.getContent().asDocument();
                } else {
                    content = new BsonDocument();
                }

                // generate id as specs mandates
                BsonValue schemaId;

                if (!content.containsKey("_id")) {
                    schemaId = new BsonObjectId(new ObjectId());
                    content.put(
                            "id",
                            schemaId);
                } else {
                    schemaId = content.get("_id");
                }

                SchemaStoreURL uri = new SchemaStoreURL(
                        context.getDBName(),
                        schemaId);

                content.put("id", new BsonString(uri.toString()));

                // escape all $ prefixed keys
                escapeSchema(_contentToTransform);

                // add (overwrite) $schema field
                if (null != _contentToTransform) {
                    _contentToTransform.put("_$schema", $SCHEMA);
                }
            } else if (context.isGet()) {
                // apply transformation on embedded schemas

                if (null != _contentToTransform && _contentToTransform.containsKey("_embedded")) {

                    BsonValue _embedded = _contentToTransform
                            .get("_embedded");

                    if (_embedded.isDocument()
                            && _embedded.asDocument()
                                    .containsKey("rh:schema")) {

                        // execute the logic on children documents
                        BsonValue docs = _embedded
                                .asDocument()
                                .get("rh:schema");

                        if (docs.isArray()) {
                            docs.asArray().stream()
                                    .filter(BsonValue::isDocument)
                                    .forEach((doc) -> {
                                        unescapeSchema(doc.asDocument());
                                    });
                        }
                    }
                }
            }
        }
    }
}
