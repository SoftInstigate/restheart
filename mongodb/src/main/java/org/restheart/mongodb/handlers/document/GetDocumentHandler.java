/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.mongodb.handlers.document;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import java.util.HashSet;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import static org.restheart.exchange.ExchangeKeys.COLL_META_DOCID_PREFIX;
import static org.restheart.exchange.ExchangeKeys.DB_META_DOCID;
import static org.restheart.exchange.ExchangeKeys.META_COLLNAME;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.utils.RequestHelper;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.BsonUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetDocumentHandler extends PipelinedHandler {
    private final Databases dbs = Databases.get();

    /**
     * Default ctor
     */
    public GetDocumentHandler() {
        super();
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public GetDocumentHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        final BsonValue docId;
        final String collName;

        // get collection name and doc id
        // handling special case /_meta that is mapped to collName=_properties
        // and docId=_properties (for db meta)
        // and docId=_properties.collName for (coll meta)
        if (request.isDbMeta()) {
            collName = META_COLLNAME;
            docId = new BsonString(DB_META_DOCID);
        } else if (request.isCollectionMeta() || request.isFilesBucketMeta() || request.isSchemaStoreMeta()) {
            collName = META_COLLNAME;
            docId = new BsonString(COLL_META_DOCID_PREFIX.concat(request.getCollectionName()));
        } else {
            collName = request.getCollectionName();
            docId = request.getDocumentId();
        }

        Bson query = eq("_id", docId);

        HashSet<Bson> terms = new HashSet<>();

        if (request.getShardKey() != null) {
            terms.add(request.getShardKey());
        }

        // filters are applied to GET /db/coll/docid as well
        // to make easy implementing filter based access restrictions
        // for instance a Trasnformer can add a filter to limit access to data
        // on the basis of the user role
        if (request.getFiltersDocument() != null) {
            terms.add(request.getFiltersDocument());
        }

        if (terms.size() > 0) {
            terms.add(query);
            query = and(terms);
        }

        final BsonDocument fieldsToReturn = new BsonDocument();

        Deque<String> keys = request.getKeys();

        if (keys != null) {
            keys.stream().forEach((String f) -> {
                BsonDocument keyQuery = BsonDocument.parse(f);

                fieldsToReturn.putAll(keyQuery);  // this can throw JsonParseException for invalid filter parameters
            });
        }

        var cs = request.getClientSession();
        var coll = dbs.getCollection(dbs.db(request.rsOps(), request.getDBName()), collName);

        var document = cs == null
                ? coll.find(query).projection(fieldsToReturn).first()
                : coll.find(cs, query).projection(fieldsToReturn).first();

        if (document == null) {
            String errMsg = request.getDocumentId() == null
                    ? " does not exist"
                    : " ".concat(BsonUtils.getIdAsString(request.getDocumentId(), true)).concat(" does not exist");

            if (null != request.getType()) {
                switch (request.getType()) {
                    case DOCUMENT:
                        errMsg = "document".concat(errMsg);
                        break;
                    case FILE:
                        errMsg = "file".concat(errMsg);
                        break;
                    case SCHEMA:
                        errMsg = "schema".concat(errMsg);
                        break;
                    case DB_META:
                        errMsg = "resource _meta is not defined for "
                                + "this db.";
                        break;
                    case COLLECTION_META:
                        errMsg = "resource _meta is not defined for "
                                + "this collection.";
                        break;
                    case SCHEMA_STORE_META:
                        errMsg = "resource _meta is not defined for "
                                + "this schema store.";
                        break;
                    case FILES_BUCKET_META:
                        errMsg = "resource _meta is not defined for "
                                + "this file bucket.";
                        break;
                    default:
                        errMsg = "resource".concat(errMsg);
                        break;
                }
            }

            response.setInError(
                    HttpStatus.SC_NOT_FOUND,
                    errMsg);
            next(exchange);
            return;
        }

        Object etag;

        if (request.getType() == TYPE.FILE) {
            if (document.containsKey("metadata")
                    && document.get("metadata").isDocument()) {
                etag = document.get("metadata").asDocument().get(("_etag"));
            } else if (document.containsKey("_etag")) {
                // backward compatibility. until version 2.0.x, _etag was not
                // in the metadata sub-document
                etag = document.get("_etag");
            } else {
                etag = null;
            }
        } else {
            etag = document.get("_etag");
        }

        // in case the request contains the IF_NONE_MATCH header with the current etag value,
        // just return 304 NOT_MODIFIED code
        if (RequestHelper.checkReadEtag(exchange, (BsonObjectId) etag)) {
            response.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
            next(exchange);
            return;
        }

        response.setContent(document);

        response.setContentTypeAsJson();
        response.setStatusCode(HttpStatus.SC_OK);

        ResponseHelper.injectEtagHeader(exchange, etag);

        next(exchange);
    }
}
