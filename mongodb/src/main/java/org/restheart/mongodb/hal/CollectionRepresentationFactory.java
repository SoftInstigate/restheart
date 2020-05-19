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
package org.restheart.mongodb.hal;

import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import static org.restheart.exchange.ExchangeKeys.FS_FILES_SUFFIX;
import org.restheart.exchange.ExchangeKeys.TYPE;
import static org.restheart.exchange.ExchangeKeys._AGGREGATIONS;
import static org.restheart.exchange.ExchangeKeys._STREAMS;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.handlers.aggregation.AbstractAggregationOperation;
import org.restheart.mongodb.utils.URLUtils;
import org.restheart.representation.IllegalQueryParamenterException;
import org.restheart.representation.RepresentationUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class CollectionRepresentationFactory
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

    /**
     *
     * @param rep
     * @param type
     * @param data
     */
    public static void addSpecialProperties(
            final Resource rep,
            final TYPE type,
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

    /**
     *
     */
    public CollectionRepresentationFactory() {
    }

    /**
     *
     * @param exchange
     * @param embeddedData
     * @param size
     * @return
     * @throws IllegalQueryParamenterException
     */
    @Override
    public Resource getRepresentation(
            HttpServerExchange exchange,
            BsonArray embeddedData,
            long size)
            throws IllegalQueryParamenterException {
        var request = MongoRequest.of(exchange);

        final String requestPath = buildRequestPath(exchange);
        final Resource rep;

        if (request.isFullHalMode()) {
            rep = createRepresentation(exchange, requestPath);
        } else {
            rep = createRepresentation(exchange, null);
        }

        if (!request.isNoProps()) {
            addProperties(rep, request);
        }

        addSizeAndTotalPagesProperties(size, request, rep);
        addAggregationsLinks(request, rep, requestPath);
        addStreamsLinks(request, rep, requestPath);
        addEmbeddedData(embeddedData, rep, requestPath, exchange);

        if (request.isFullHalMode()) {
            addSpecialProperties(
                    rep,
                    request.getType(),
                    request.getCollectionProps());

            addPaginationLinks(exchange, size, rep);
            addLinkTemplates(request, rep, requestPath);
        }

        return rep;
    }

    private void addProperties(
            final Resource rep,
            final MongoRequest request) {
        // add the collection properties
        final BsonDocument collProps = request.getCollectionProps();

        rep.addProperties(collProps);
    }

    private void addEmbeddedData(
            BsonArray embeddedData,
            final Resource rep,
            final String requestPath,
            final HttpServerExchange exchange)
            throws IllegalQueryParamenterException {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);

            if (!embeddedData.isEmpty()) {
                embeddedDocuments(
                        embeddedData,
                        requestPath,
                        exchange,
                        rep);
            }
        } else {
            rep.addProperty(_RETURNED, new BsonInt32(0));
        }
    }

    private void addAggregationsLinks(
            final MongoRequest request,
            final Resource rep,
            final String requestPath) {
        BsonValue _aggregations = request
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
                                        + _AGGREGATIONS + "/"
                                        + uri));
                    }
                }
            });
        }
    }

    private void addStreamsLinks(
            final MongoRequest request,
            final Resource rep,
            final String requestPath) {
        BsonValue _streams = request
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
                                        + _STREAMS + "/"
                                        + uri));
                    }
                }
            });
        }
    }

    private void addLinkTemplates(
            final MongoRequest request,
            final Resource rep,
            final String requestPath) {
        // link templates and curies
        if (request.isParentAccessible()) {
            // this can happen due to mongo-mounts mapped URL
            rep.addLink(new Link(RHDB, URLUtils.getParentPath(requestPath)));
        }

        if (TYPE.FILES_BUCKET.equals(request.getType())) {
            rep.addLink(new Link(
                    RHBUCKET,
                    URLUtils.getParentPath(requestPath)
                    + "/{bucketname}"
                    + FS_FILES_SUFFIX,
                    true));
            rep.addLink(new Link(
                    RHFILE,
                    requestPath + "/{fileid}{?id_type}",
                    true));
        } else if (TYPE.COLLECTION.equals(request.getType())) {

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
                + request.getDBName()
                + "/" + request.getCollectionName()
                + "/_indexes"));

        rep.addLink(new Link(RHFILTER, requestPath + "{?filter}", true));
        rep.addLink(new Link(RHSORT, requestPath + "{?sort_by}", true));
        rep.addLink(new Link(RHPAGING, requestPath + "{?page}{&pagesize}", true));
        rep.addLink(new Link(RHINDEXES, requestPath + "/_indexes"));
    }

    private void embeddedDocuments(
            BsonArray embeddedData,
            String requestPath,
            HttpServerExchange exchange,
            Resource rep)
            throws IllegalQueryParamenterException {
        var request = MongoRequest.of(exchange);

        for (BsonValue _d : embeddedData) {
            if (_d != null && _d.isDocument()) {
                var d = _d.asDocument();

                BsonValue _id = d.get(_ID);

                if (_id != null
                        && MongoRequest.isReservedCollectionName(_id.toString())) {
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
                                        d);
                    } else {
                        nrep = new DocumentRepresentationFactory()
                                .getRepresentation(RepresentationUtils.getReferenceLink(requestPath, _id),
                                        exchange,
                                        d);
                    }

                    if (null == request.getType()) {
                        if (request.isFullHalMode()) {
                            DocumentRepresentationFactory.addSpecialProperties(
                                    nrep,
                                    TYPE.DOCUMENT,
                                    d);
                        }

                        rep.addChild(RHDOC, nrep);
                    } else {
                        switch (request.getType()) {
                            case FILES_BUCKET:
                                if (request.isFullHalMode()) {
                                    DocumentRepresentationFactory.addSpecialProperties(
                                            nrep,
                                            TYPE.FILE,
                                            d);
                                }
                                rep.addChild(RHFILE, nrep);
                                break;
                            case SCHEMA_STORE:
                                if (request.isFullHalMode()) {
                                    DocumentRepresentationFactory.addSpecialProperties(
                                            nrep,
                                            TYPE.SCHEMA,
                                            d);
                                }
                                rep.addChild(RHSCHEMA, nrep);
                                break;
                            default:
                                if (request.isFullHalMode()) {
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
}
