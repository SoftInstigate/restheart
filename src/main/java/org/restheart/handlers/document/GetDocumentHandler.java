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
package org.restheart.handlers.document;

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
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import static org.restheart.handlers.RequestContext.COLL_META_DOCID_PREFIX;
import static org.restheart.handlers.RequestContext.DB_META_DOCID;
import static org.restheart.handlers.RequestContext.META_COLLNAME;
import org.restheart.handlers.RequestContext.TYPE;
import org.restheart.representation.Resource;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;
import org.restheart.utils.RequestHelper;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetDocumentHandler extends PipedHttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetDocumentHandler.class);

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
    public GetDocumentHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        final BsonValue docId;
        final String collName;

        // get collection name and doc id
        // handling special case /_meta that is mapped to collName=_properties
        // and docId=_properties (for db meta) 
        // and docId=_properties.collName for (coll meta)
        if (context.isDbMeta()) {
            collName = META_COLLNAME;
            docId = new BsonString(DB_META_DOCID);
        } else if (context.isCollectionMeta()) {
            collName = META_COLLNAME;
            docId = new BsonString(COLL_META_DOCID_PREFIX.concat(
                    context.getCollectionName()));
        } else {
            collName = context.getCollectionName();
            docId = context.getDocumentId();
        }

        Bson query = eq("_id", docId);

        HashSet<Bson> terms = new HashSet<>();

        if (context.getShardKey() != null) {
            terms.add(context.getShardKey());
        }

        // filters are applied to GET /db/coll/docid as well 
        // to make easy implementing filter based access restrictions
        // for instance a Trasnformer can add a filter to limit access to data
        // on the basis of the user role
        if (context.getFiltersDocument() != null) {
            terms.add(context.getFiltersDocument());
        }

        if (terms.size() > 0) {
            terms.add(query);
            query = and(terms);
        }

        final BsonDocument fieldsToReturn = new BsonDocument();

        Deque<String> keys = context.getKeys();

        if (keys != null) {
            keys.stream().forEach((String f) -> {
                BsonDocument keyQuery = BsonDocument.parse(f);

                fieldsToReturn.putAll(keyQuery);  // this can throw JsonParseException for invalid filter parameters
            });
        }

        var cs = context.getClientSession();
        var coll = getDatabase().getCollection(
                context.getDBName(),
                collName);

        BsonDocument document = cs == null
                ? coll
                        .find(query)
                        .projection(fieldsToReturn)
                        .first()
                : coll
                        .find(cs, query)
                        .projection(fieldsToReturn)
                        .first();

        if (document == null) {
            String errMsg = context.getDocumentId() == null
                    ? " does not exist"
                    : " ".concat(JsonUtils.getIdAsString(
                            context.getDocumentId(), true))
                            .concat(" does not exist");

            if (null != context.getType()) {
                switch (context.getType()) {
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

            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_FOUND,
                    errMsg);
            next(exchange, context);
            return;
        }

        Object etag;

        if (context.getType() == TYPE.FILE) {
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
            context.setResponseStatusCode(HttpStatus.SC_NOT_MODIFIED);
            next(exchange, context);
            return;
        }

        String requestPath = URLUtils.removeTrailingSlashes(
                exchange.getRequestPath());

        context.setResponseContent(new DocumentRepresentationFactory()
                .getRepresentation(
                        requestPath,
                        exchange,
                        context,
                        document)
                .asBsonDocument());

        context.setResponseContentType(Resource.HAL_JSON_MEDIA_TYPE);
        context.setResponseStatusCode(HttpStatus.SC_OK);

        ResponseHelper.injectEtagHeader(exchange, etag);

        next(exchange, context);
    }
}
