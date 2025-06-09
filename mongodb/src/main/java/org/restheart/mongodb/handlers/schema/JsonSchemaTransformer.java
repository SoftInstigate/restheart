/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * On write, escapes schema properties ($ prefixed) On read, unescape schema
 * properties
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonSchemaTransformer extends PipelinedHandler {

    static final Logger LOGGER = LoggerFactory
            .getLogger(JsonSchemaTransformer.class);

    private static final BsonString $SCHEMA = new BsonString("http://json-schema.org/draft-04/schema#");

    private final boolean phase;

    /**
     *
     * @param phase true for request, false for response
     */
    public JsonSchemaTransformer(boolean phase) {
        super(null);
        this.phase = phase;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);

        BsonValue content;

        if (this.phase) { // request
            content = request.getContent();

        } else { // response
            var response = MongoResponse.of(exchange);
            content = response.getContent();
        }

        if (content != null) {
            if (content.isDocument()) {
                transform(request, content.asDocument());
            } else if (content.isArray()) {
                content.asArray().stream()
                        .filter(doc -> doc.isDocument())
                        .map(doc -> doc.asDocument())
                        .forEachOrdered(doc -> transform(request, doc));
            }
        }

        next(exchange);
    }

    private void transform(MongoRequest request, BsonDocument document) {
        if (request.isInError()) {
            return;
        }

        if (request.isSchema()) {
            if (request.isGet()) {
                unescapeSchema(document);
            } else if (request.isPut() || request.isPatch()) {
                BsonDocument content;

                if (request.getContent() != null) {
                    content = request.getContent().asDocument();
                } else {
                    content = new BsonDocument();
                }

                // generate id as specs mandates
                SchemaStoreURL uri = new SchemaStoreURL(request.getDBName(), request.getDocumentId());

                content.put("id", new BsonString(uri.toString()));

                // escape all $ prefixed keys
                escapeSchema(document);

                // add (overwrite) $schema field
                if (null != document) {
                    document.put("_$schema", $SCHEMA);
                }
            }
        } else if (request.isSchemaStore()) {
            if (request.isPost()) {
                BsonDocument content;

                if (request.getContent() != null) {
                    content = request.getContent().asDocument();
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
                        request.getDBName(),
                        schemaId);

                content.put("id", new BsonString(uri.toString()));

                // escape all $ prefixed keys
                escapeSchema(document);

                // add (overwrite) $schema field
                if (null != document) {
                    document.put("_$schema", $SCHEMA);
                }
            } else if (request.isGet() && null != document && document.isDocument()) {
                unescapeSchema(document.asDocument());
            }
        }
    }

    /**
     *
     * @param schema
     */
    static void escapeSchema(BsonDocument schema) {
        BsonValue escaped = BsonUtils.escapeKeys(schema, false);

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
    static void unescapeSchema(BsonDocument schema) {
        var unescaped = BsonUtils.unescapeKeys(schema);

        if (unescaped != null && unescaped.isDocument()) {
            var keys = Lists.newArrayList(schema.keySet().iterator());

            keys.stream().forEach(f -> schema.remove(f));

            schema.putAll(unescaped.asDocument());
        }
    }
}
