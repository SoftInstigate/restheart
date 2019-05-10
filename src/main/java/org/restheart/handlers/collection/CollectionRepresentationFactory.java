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
package org.restheart.handlers.collection;

import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.Configuration;
import org.restheart.representation.AbstractRepresentationFactory;
import org.restheart.representation.Link;
import org.restheart.representation.Resource;
import org.restheart.representation.UnsupportedDocumentIdException;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.TYPE;
import org.restheart.handlers.aggregation.AbstractAggregationOperation;
import org.restheart.handlers.document.DocumentRepresentationFactory;
import org.restheart.handlers.metadata.InvalidMetadataException;
import org.restheart.plugins.checkers.JsonSchemaChecker;
import org.restheart.metadata.CheckerMetadata;
import org.restheart.representation.RepUtils;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CollectionRepresentationFactory
        extends AbstractRepresentationFactory {

    // TODO this is hardcoded, if name of checker is changed in conf file
// method won't work. need to get the name from the configuration
    private static final String JSON_SCHEMA_NAME = "jsonSchema";

    private static final String _ETAG = "_etag";
    private static final String _TYPE = "_type";
    private static final String _LASTUPDATED_ON = "_lastupdated_on";

    private static final String _RETURNED = "_returned";

    private static final String RHINDEXES = "rh:indexes";
    private static final String RHPAGING = "rh:paging";
    private static final String RHSORT = "rh:sort";
    private static final String RHFILTER = "rh:filter";
    private static final String RHDB = "rh:db";
    private static final String RHBUCKET = "rh:bucket";
    private static final String RHCOLL = "rh:coll";
    private static final String RHDOCUMENT = "rh:document";

    private static final String _ID = "_id";
    private static final String RHFILE = "rh:file";
    private static final String RHSCHEMA = "rh:schema";
    private static final String RHDOC = "rh:doc";
    
    private static final String STREAMS_ELEMENT_NAME = "streams";

    public static void addSpecialProperties(
            final Resource rep,
            final RequestContext.TYPE type,
            final BsonDocument data) {
        rep.addProperty(_TYPE, new BsonString(type.name()));

        Object etag = data.get(_ETAG);

        if (etag != null && etag instanceof ObjectId) {
            if (data.get(_LASTUPDATED_ON) == null) {
                // add the _lastupdated_on in case the _etag field is present and its value is an ObjectId
                rep.addProperty(_LASTUPDATED_ON,
                        new BsonString(Instant.ofEpochSecond(
                                ((ObjectId) etag).getTimestamp()).toString()));
            }
        }
    }

    private static void addSchemaLinks(
            Resource rep,
            RequestContext context) {
        try {
            List<CheckerMetadata> checkers
                    = CheckerMetadata.getFromJson(context.getCollectionProps());

            if (checkers != null) {
                checkers
                        .stream().filter((CheckerMetadata c) -> {
                            return JSON_SCHEMA_NAME.equals(c.getName());
                        }).forEach((CheckerMetadata c) -> {
                    BsonValue schemaId = c.getArgs().asDocument()
                            .get(JsonSchemaChecker.SCHEMA_ID_PROPERTY);

                    BsonValue _schemaStoreDb = c.getArgs().asDocument()
                            .get(JsonSchemaChecker.SCHEMA_STORE_DB_PROPERTY);

                    // just in case the checker is missing the mandatory schemaId property
                    if (schemaId == null) {
                        return;
                    }

                    String schemaStoreDb;

                    if (_schemaStoreDb == null) {
                        schemaStoreDb = context.getDBName();
                    } else {
                        schemaStoreDb = _schemaStoreDb.toString();
                    }

                    try {
                        rep.addLink(new Link("schema", URLUtils
                                .getUriWithDocId(context,
                                        schemaStoreDb, "_schemas", schemaId)));
                    } catch (UnsupportedDocumentIdException ex) {
                    }
                });

            }
        } catch (InvalidMetadataException ime) {
            // nothing to do
        }
    }

    public CollectionRepresentationFactory() {
    }

    /**
     *
     * @param exchange
     * @param context
     * @param embeddedData
     * @param size
     * @return
     * @throws IllegalQueryParamenterException
     */
    @Override
    public Resource getRepresentation(
            HttpServerExchange exchange,
            RequestContext context,
            List<BsonDocument> embeddedData,
            long size)
            throws IllegalQueryParamenterException {
        final String requestPath = buildRequestPath(exchange);
        final Resource rep;

        if (context.isFullHalMode()) {
            rep = createRepresentation(exchange, context, requestPath);
        } else {
            rep = createRepresentation(exchange, context, null);
        }

        if (!context.isNoProps()) {
            addProperties(rep, context);
        }

        addSizeAndTotalPagesProperties(size, context, rep);
        addAggregationsLinks(context, rep, requestPath);
        addStreamsLinks(context, rep, requestPath);
        addSchemaLinks(rep, context);
        addEmbeddedData(embeddedData, rep, requestPath, exchange, context);

        if (context.isFullHalMode()) {
            addSpecialProperties(
                    rep,
                    context.getType(),
                    context.getCollectionProps());

            addPaginationLinks(exchange, context, size, rep);
            addLinkTemplates(context, rep, requestPath);
        }

        return rep;
    }

    private void addProperties(
            final Resource rep,
            final RequestContext context) {
        // add the collection properties
        final BsonDocument collProps = context.getCollectionProps();

        rep.addProperties(collProps);
    }

    private void addEmbeddedData(
            List<BsonDocument> embeddedData,
            final Resource rep,
            final String requestPath,
            final HttpServerExchange exchange,
            final RequestContext context)
            throws IllegalQueryParamenterException {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);

            if (!embeddedData.isEmpty()) {
                embeddedDocuments(
                        embeddedData,
                        requestPath,
                        exchange,
                        context,
                        rep);
            }
        } else {
            rep.addProperty(_RETURNED, new BsonInt32(0));
        }
    }

    private void addAggregationsLinks(
            final RequestContext context,
            final Resource rep,
            final String requestPath) {
        BsonValue _aggregations = context
                .getCollectionProps()
                .get(AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME);

        if (_aggregations != null && _aggregations.isArray()) {
            BsonArray aggregations = _aggregations.asArray();

            aggregations.forEach(q -> {
                if (q.isDocument()) {
                    BsonValue _uri = q.asDocument().get("uri");

                    if (_uri != null && _uri.isString()) {
                        String uri = _uri.asString().getValue();

                        rep.addLink(
                                new Link(uri,
                                        requestPath
                                        + "/"
                                        + RequestContext._AGGREGATIONS + "/"
                                        + uri));
                    }
                }
            });
        }
    }
    
    private void addStreamsLinks(
            final RequestContext context,
            final Resource rep,
            final String requestPath) {
        BsonValue _streams = context
                .getCollectionProps()
                .get(STREAMS_ELEMENT_NAME);

        if (_streams != null && _streams.isArray()) {
            BsonArray streams = _streams.asArray();

            streams.forEach(q -> {
                if (q.isDocument()) {
                    BsonValue _uri = q.asDocument().get("uri");

                    if (_uri != null && _uri.isString()) {
                        String uri = _uri.asString().getValue();

                        rep.addLink(
                                new Link(uri,
                                        requestPath
                                        + "/"
                                        + RequestContext._STREAMS + "/"
                                        + uri));
                    }
                }
            });
        }
    }

    private void addLinkTemplates(
            final RequestContext context,
            final Resource rep,
            final String requestPath) {
        // link templates and curies
        if (context.isParentAccessible()) {
            // this can happen due to mongo-mounts mapped URL
            rep.addLink(new Link(RHDB, URLUtils.getParentPath(requestPath)));
        }

        if (TYPE.FILES_BUCKET.equals(context.getType())) {
            rep.addLink(new Link(
                    RHBUCKET,
                    URLUtils.getParentPath(requestPath)
                    + "/{bucketname}"
                    + RequestContext.FS_FILES_SUFFIX,
                    true));
            rep.addLink(new Link(
                    RHFILE,
                    requestPath + "/{fileid}{?id_type}",
                    true));
        } else if (TYPE.COLLECTION.equals(context.getType())) {

            rep.addLink(new Link(
                    RHCOLL,
                    URLUtils.getParentPath(requestPath) + "/{collname}",
                    true));
            rep.addLink(new Link(
                    RHDOCUMENT,
                    requestPath + "/{docid}{?id_type}",
                    true));
        }

        rep.addLink(new Link(RHINDEXES,
                requestPath
                + "/"
                + context.getDBName()
                + "/" + context.getCollectionName()
                + "/_indexes"));

        rep.addLink(new Link(RHFILTER, requestPath + "{?filter}", true));
        rep.addLink(new Link(RHSORT, requestPath + "{?sort_by}", true));
        rep.addLink(new Link(RHPAGING, requestPath + "{?page}{&pagesize}", true));
        rep.addLink(new Link(RHINDEXES, requestPath + "/_indexes"));
    }

    private void embeddedDocuments(
            List<BsonDocument> embeddedData,
            String requestPath,
            HttpServerExchange exchange,
            RequestContext context,
            Resource rep)
            throws IllegalQueryParamenterException {
        for (BsonDocument d : embeddedData) {
            BsonValue _id = d.get(_ID);

            if (_id != null
                    && RequestContext.isReservedResourceCollection(
                            _id.toString())) {
                rep.addWarning("filtered out reserved resource "
                        + requestPath + "/"
                        + _id.toString());
            } else {
                Resource nrep;

                if (_id == null) {
                    nrep = new DocumentRepresentationFactory()
                            .getRepresentation(
                                    requestPath + "/_null",
                                    exchange,
                                    context,
                                    d);
                } else {
                    nrep = new DocumentRepresentationFactory()
                            .getRepresentation(
                                    RepUtils.getReferenceLink(requestPath, _id),
                                    exchange,
                                    context,
                                    d);
                }

                if (null == context.getType()) {
                    if (context.isFullHalMode()) {
                        DocumentRepresentationFactory.addSpecialProperties(
                                nrep,
                                TYPE.DOCUMENT,
                                d);
                    }

                    rep.addChild(RHDOC, nrep);
                } else {
                    switch (context.getType()) {
                        case FILES_BUCKET:
                            if (context.isFullHalMode()) {
                                DocumentRepresentationFactory.addSpecialProperties(
                                        nrep,
                                        TYPE.FILE,
                                        d);
                            }
                            rep.addChild(RHFILE, nrep);
                            break;
                        case SCHEMA_STORE:
                            if (context.isFullHalMode()) {
                                DocumentRepresentationFactory.addSpecialProperties(
                                        nrep,
                                        TYPE.SCHEMA,
                                        d);
                            }
                            rep.addChild(RHSCHEMA, nrep);
                            break;
                        default:
                            if (context.isFullHalMode()) {
                                DocumentRepresentationFactory.addSpecialProperties(
                                        nrep,
                                        TYPE.DOCUMENT,
                                        d);
                            }
                            rep.addChild(RHDOC, nrep);
                            break;
                    }
                }
            }
        }
    }

}
