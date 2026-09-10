/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
import org.bson.BsonDocument;
import org.bson.BsonValue;
import static org.restheart.exchange.ExchangeKeys._SCHEMAS;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.UnsupportedDocumentIdException;
import org.restheart.mongodb.utils.MongoURLUtils;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.schema.JsonSchemaNotFoundException;
import org.restheart.plugins.schema.JsonSchemas;
import org.restheart.plugins.schema.SchemaValidationException;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Checks documents according to the specified JSON schema
 *
 * This intercetor is able to check PUT and POST requests that don't use update
 * operators. PATCH requests are checked by jsonSchemaAfterWrite
 * <br><br>
 * Bulk PATCH, i.e. PATCH /coll/*, is checked by jsonSchemaAfterWrite too, where the
 * write runs in a transaction — on a standalone MongoDB it cannot be checked, and
 * the optional metadata property 'skipNotSupported' controls the behaviour: if
 * true, the request is not checked and executed, if false the request fails.
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
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        // execute after any other request interceptor
        priority = Integer.MAX_VALUE)
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

    @Inject("json-schemas")
    private JsonSchemas jsonSchemas;

    protected JsonSchemas jsonSchemas() {
        return jsonSchemas;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        // Nothing to validate once the request has already been refused: the
        // document is never going to be written, and checking it here replaces
        // whatever the refusal said with a list of missing keys.
        //
        // This runs at Integer.MAX_VALUE, so it runs after every other request
        // interceptor — including the ones that rewrite a request body into the
        // document to store. When one of those rejects, the body is still what
        // the client sent, and validating *that* against the collection's schema
        // reports every required field as missing. A shopper told "this is out
        // of stock" was instead shown twelve schema violations naming fields
        // they had never heard of and could not have supplied.
        //
        // The check belongs here and not in `resolve`: the executor evaluates
        // every `resolve` up front, in one pass, and only then runs the
        // interceptors in order — so at resolve time the interceptor that
        // refuses the request has not run yet and nothing is in error.
        if (response.isInError()) {
            return;
        }

        var args = request.getCollectionProps()
                .get("jsonSchema")
                .asDocument();

        if (handledElsewhere(request, response, args)) {
            return;
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
            MongoURLUtils.checkId(schemaId);
        } catch (UnsupportedDocumentIdException ex) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "wrong 'jsonSchema': "
                            + "schema 'id' is not valid", ex);
            return;
        }

        try {
            jsonSchemas().validate(documentsToCheck(request, response), schemaStoreDb, schemaId);
        } catch (JsonSchemaNotFoundException ex) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "wrong 'jsonSchema': schema "
                            + schemaStoreDb + "/" + _SCHEMAS + "/"
                            + BsonUtils.getIdAsString(schemaId, false)
                            + " not found");
        } catch (SchemaValidationException sve) {
            response.setInError(HttpStatus.SC_BAD_REQUEST,
                    "Request content violates schema "
                            + BsonUtils.getIdAsString(schemaId, true)
                            + ": "
                            + String.join(", ", sve.getViolations()));
        }
    }

    /**
     * Whether this request is not this interceptor's to check, and what to do about it.
     *
     * <p>A bulk {@code PATCH} carries update operators, so before the write there is no resulting
     * document to validate. Where the write runs in a transaction it is checked afterwards instead,
     * by {@code jsonSchemaAfterWrite}, and this pass simply lets it past. Where it does not — no
     * replica set, so nothing could undo it — it is refused, unless the collection has opted out
     * with {@code skipNotSupported}.
     *
     * <p>Overridden by {@link JsonSchemaAfterWriteChecker}, which inherits this {@code handle} and
     * for which none of the above applies: checking a bulk {@code PATCH} after the write is exactly
     * its job, and returning early here would skip the one case it exists for.
     *
     * @return true if the caller must stop, having either refused the request or deliberately let
     * it through
     */
    boolean handledElsewhere(MongoRequest request, MongoResponse response, BsonDocument args) {
        if (!(request.isPatch() && request.isBulkDocuments())) {
            return false;
        }

        if (request.isTxnRequested()) {
            // jsonSchemaAfterWriteTxn asked for the transaction, and only asks where one is
            // available; jsonSchemaAfterWrite validates the outcome and aborts it if it fails
            return true;
        }

        var skipNotSupported = args.get(SKIP_NOT_SUPPORTED_PROPERTY);

        if (skipNotSupported != null
                && skipNotSupported.isBoolean()
                && skipNotSupported.asBoolean().getValue()) {
            LOGGER.debug("skipping jsonSchema checking since the request is a bulk PATCH and skipNotSupported=true");
            return true;
        }

        response.setInError(HttpStatus.SC_NOT_IMPLEMENTED,
                "'jsonSchema' checker does not support bulk PATCH requests on a standalone "
                        + "MongoDB. Use a replica set, where the write runs in a transaction "
                        + "and is validated after it, or set 'skipNotSupported:true' to allow "
                        + "them unvalidated.");

        return true;
    }

    List<BsonDocument> documentsToCheck(MongoRequest request, MongoResponse response) {
        var ret = new ArrayList<BsonDocument>();

        var content = request.getContent() == null
                ? new BsonDocument()
                : request.getContent();

        if (content.isDocument()) {
            ret.add(content.asDocument());
        } else if (content.isArray()) {
            content.asArray()
                    .stream()
                    .filter(doc -> doc.isDocument())
                    .map(doc -> doc.asDocument())
                    .forEachOrdered(ret::add);
        }

        return ret;
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                // a bulk PATCH is not a "write document": its resource type is BULK_DOCUMENTS, so
                // isWriteDocument() is false for it. Repeating that condition outside the || — as
                // this did — silently cancelled the branch that had just admitted it, and the 501
                // below never fired: bulk PATCH went through unvalidated.
                && ((request.isWriteDocument() && !request.isPatch())
                || (request.isPatch() && request.isBulkDocuments()))
                && request.getCollectionProps() != null
                && request.getCollectionProps().containsKey("jsonSchema")
                && request.getCollectionProps().get("jsonSchema").isDocument();
    }
}
