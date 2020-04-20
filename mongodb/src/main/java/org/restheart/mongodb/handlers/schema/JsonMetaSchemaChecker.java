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

import io.undertow.server.HttpServerExchange;
import java.io.InputStream;
import java.util.ArrayList;
import org.bson.BsonDocument;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * checks the schema of the schemas using json metaschema
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonMetaSchemaChecker extends PipelinedHandler {

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

    /**
     *
     * @param exchange
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = (MongoRequest) MongoRequest.of(exchange);
        var response = (MongoResponse) MongoResponse.of(exchange);

        if (!request.isWriteDocument() || request.isPatch()) {
            next(exchange);
            return;
        }

        var contentToCheck = request.getContent() == null
                ? new BsonDocument()
                : request.getContent();

        try {
            schema.validate(new JSONObject(contentToCheck.toString()));
        } catch (ValidationException ve) {
            var errors = new ArrayList<String>();

            errors.add(ve.getMessage().replaceAll("#: ", ""));

            ve.getCausingExceptions().stream()
                    .map(ValidationException::getMessage)
                    .forEach(errors::add);

            var errMsgBuilder = new StringBuilder();

            errors.stream()
                    .map(e -> e.replaceAll("#: ", ""))
                    .forEachOrdered(e -> errMsgBuilder.append(e).append(", "));

            var errMsg = errMsgBuilder.toString();

            if (errMsg.length() > 2
                    && ", ".equals(errMsg.substring(errMsg.length() - 2, errMsg.length()))) {
                errMsg = errMsg.substring(0, errMsg.length() - 2);

            }

            response.setInError(HttpStatus.SC_BAD_REQUEST,
                    "Request content violates JSON Schema meta schema: "
                    + errMsg);
        }

        next(exchange);
    }
}
