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
package org.restheart.mongodb.interceptors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import static org.restheart.exchange.ExchangeKeys._SCHEMAS;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.handlers.schema.JsonSchemaCacheSingleton;
import org.restheart.mongodb.handlers.schema.JsonSchemaNotFoundException;
import org.restheart.mongodb.utils.URLUtils;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.representation.UnsupportedDocumentIdException;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Checks documents according to the specified JSON schema
 *
 * This intercetor is able to check PUT and POST requests that don't use update
 * operators. PATCH requests are checked by jsonSchemaAfterWrite
 * <br><br>
 * Note that checking bulk PATCH, i.e. PATCH /coll/*, is not supported. In this
 * case the optional metadata property 'skipNotSuppored' controls the behaviour:
 * if true, the request is not checked and executed, if false the request fails.
 *
 * It checks the request content against the JSON schema specified by the
 * 'jsonSchema' collection metadata:
 * <br><br>
 * { "jsonSchema": { "schemaId": &lt;schemaId&gt; "schemaStoreDb":
 * &lt;schemaStoreDb&gt;, "skipNotSupported": &lt;boolean&gt; } }
 * <br><br>
 * schemaStoreDb is optional, default value is same db, skipNotSuppored is
 * optional, defaul value is false
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "jsonSchemaBeforeWrite",
        description = "Checks the request content against the JSON schema specified by the 'jsonSchema' collection metadata",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
@SuppressWarnings("deprecation")
public class JsonSchemaBeforeWriteChecker implements MongoInterceptor {

    /**
     *
     */
    public static final String SCHEMA_STORE_DB_PROPERTY = "schemaStoreDb";

    /**
     *
     */
    public static final String SCHEMA_ID_PROPERTY = "schemaId";

    /**
     *
     */
    public static final String SKIP_NOT_SUPPORTED_PROPERTY = "skipNotSupported";

    static final Logger LOGGER
            = LoggerFactory.getLogger(JsonSchemaBeforeWriteChecker.class);

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var args = request.getCollectionProps()
                .get("jsonSchema")
                .asDocument();

        // this request is not supported by jsonSchema checkers
        // 
        if (request.isPatch() && request.isBulkDocuments()) {
            BsonValue skipNotSupported = args.get(SKIP_NOT_SUPPORTED_PROPERTY);

            if (skipNotSupported != null
                    && skipNotSupported.isBoolean()
                    && skipNotSupported.asBoolean().getValue()) {
                LOGGER.debug("skipping jsonSchema checking since the request is a bulk PATCH and skipNotSupported=true");
                return;
            } else {
                response.setInError(HttpStatus.SC_NOT_IMPLEMENTED,
                        "'jsonSchema' checker does not support bulk PATCH requests. "
                        + "Set 'skipNotSupported:true' to allow them.");
                return;
            }
        }

        BsonValue _schemaStoreDb = args.get(SCHEMA_STORE_DB_PROPERTY);
        String schemaStoreDb;

        BsonValue schemaId = args.get(SCHEMA_ID_PROPERTY);

        if (schemaId == null) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "wrong 'jsonSchema': missing property "
                    + SCHEMA_ID_PROPERTY);
            return;
        }

        if (_schemaStoreDb == null) {
            // if not specified assume the current db as the schema store db
            schemaStoreDb = request.getDBName();
        } else if (_schemaStoreDb.isString()) {
            schemaStoreDb = _schemaStoreDb.asString().getValue();
        } else {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "wrong 'jsonSchema': "
                    + "property "
                    + SCHEMA_STORE_DB_PROPERTY
                    + " must be a string");
            return;
        }

        try {
            URLUtils.checkId(schemaId);
        } catch (UnsupportedDocumentIdException ex) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "wrong 'jsonSchema': "
                    + "schema 'id' is not valid", ex);
            return;
        }

        Schema theschema;

        try {
            theschema = JsonSchemaCacheSingleton
                    .getInstance()
                    .get(schemaStoreDb, schemaId);
        } catch (JsonSchemaNotFoundException ex) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "wrong 'jsonSchema': schema "
                    + schemaStoreDb + "/" + _SCHEMAS + "/"
                    + JsonUtils.getIdAsString(schemaId, false)
                    + " not found");
            return;
        }

        if (Objects.isNull(theschema)) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "wrong 'jsonSchema': schema "
                    + schemaStoreDb + "/" + _SCHEMAS + "/"
                    + JsonUtils.getIdAsString(schemaId, false)
                    + " not found");
            return;
        }

        documentsToCheck(request, response)
                .stream()
                .forEachOrdered(doc -> {

                    try {
                        theschema.validate(doc);
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
                                "Request content violates schema "
                                + JsonUtils.getIdAsString(schemaId, true)
                                + ": "
                                + errMsg);
                    }
                });
    }

    List<JSONObject> documentsToCheck(MongoRequest request, MongoResponse response) {
        var ret = new ArrayList<JSONObject>();

        var content = request.getContent() == null
                ? new BsonDocument()
                : request.getContent();

        if (content.isDocument()) {
            ret.add(new JSONObject(content.asDocument().toJson()));
        } else if (content.isArray()) {
            content.asArray()
                    .stream()
                    .filter(doc -> doc.isDocument())
                    .map(doc -> doc.asDocument().toJson())
                    .map(doc -> new JSONObject(doc))
                    .forEachOrdered(ret::add);
        }

        return ret;
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && ((request.isWriteDocument() && !request.isPatch())
                || (request.isPatch() && request.isBulkDocuments()))
                && request.getCollectionProps() != null
                && request.getCollectionProps()
                        .containsKey("jsonSchema")
                && request.getCollectionProps()
                        .get("jsonSchema")
                        .isDocument();
    }
}
