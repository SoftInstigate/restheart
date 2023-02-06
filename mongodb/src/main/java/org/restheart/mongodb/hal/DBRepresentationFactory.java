/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
import static org.restheart.exchange.ExchangeKeys._SCHEMAS;
import org.restheart.exchange.IllegalQueryParamenterException;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.utils.MongoURLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class DBRepresentationFactory extends AbstractRepresentationFactory {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(DBRepresentationFactory.class);

    /**
     *
     * @param rep
     * @param type
     * @param data
     */
    public static void addSpecialProperties(
            final Resource rep,
            TYPE type,
            BsonDocument data) {
        rep.addProperty("_type", new BsonString(type.name()));

        Object etag = data.get("_etag");

        if (etag != null && etag instanceof ObjectId) {
            if (data.get("_lastupdated_on") == null) {
                // add the _lastupdated_on in case the _etag field is present and its value is an ObjectId
                rep.addProperty("_lastupdated_on",
                        new BsonString(Instant.ofEpochSecond(((ObjectId) etag)
                                .getTimestamp()).toString()));
            }
        }
    }

    /**
     *
     */
    public DBRepresentationFactory() {
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

        addEmbeddedData(embeddedData, request, rep, requestPath);

        if (request.isFullHalMode()) {

            addPaginationLinks(exchange, size, rep);

            addSpecialProperties(rep, request.getType(), request.getDbProps());

            addLinkTemplates(request, rep, requestPath);
        }

        return rep;
    }

    private void addProperties(
            final Resource rep,
            MongoRequest request) {
        final BsonDocument dbProps = request.getDbProps();

        rep.addProperties(dbProps);
    }

    private void addEmbeddedData(
            final BsonArray embeddedData,
            final MongoRequest request,
            final Resource rep,
            final String requestPath) {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);

            if (!embeddedData.isEmpty()) {
                embeddedCollections(embeddedData, request, requestPath, rep);
            }
        } else {
            rep.addProperty("_returned", new BsonInt32(0));
        }
    }

    private void addLinkTemplates(
            final MongoRequest request,
            final Resource rep,
            final String requestPath) {
        String parentPath = MongoURLUtils.getParentPath(requestPath);

        // link templates and curies
        if (request.isParentAccessible()) {
            // this can happen due to mongo-mounts mapped URL
            rep.addLink(new Link("rh:root", parentPath));
        }

        if (parentPath.endsWith("/")) {
            rep.addLink(new Link("rh:db", MongoURLUtils.removeTrailingSlashes(
                    MongoURLUtils.getParentPath(requestPath)) + "{dbname}", true));
        } else {
            rep.addLink(new Link("rh:db", MongoURLUtils.removeTrailingSlashes(
                    MongoURLUtils.getParentPath(requestPath)) + "/{dbname}", true));
        }

        rep.addLink(new Link("rh:coll", requestPath + "/{collname}", true));
        rep.addLink(new Link("rh:bucket", requestPath
                + "/{bucketname}"
                + FS_FILES_SUFFIX, true));

        rep.addLink(new Link("rh:paging", requestPath
                + "{?page}{&pagesize}", true));
    }

    private void embeddedCollections(
            final BsonArray embeddedData,
            final MongoRequest request,
            final String requestPath,
            final Resource rep) {
        embeddedData.stream()
                .filter(d -> d != null)
                .filter(d -> d.isDocument())
                .map(d -> d.asDocument())
                .forEach((d) -> {
                    BsonValue _id = d.get("_id");

                    if (_id != null && _id.isString()) {
                        BsonString id = _id.asString();

                        // avoid starting double slash in self href for root URI
                        String rp = MongoURLUtils.removeTrailingSlashes(requestPath);
                        rp = "/".equals(rp) ? "" : rp;

                        final Resource nrep;

                        if (request.isFullHalMode()) {
                            nrep = new Resource(rp + "/" + id.getValue());
                        } else {
                            nrep = new Resource();
                        }

                        nrep.addProperties(d);

                        if (id.getValue().endsWith(FS_FILES_SUFFIX)) {
                            if (request.isFullHalMode()) {
                                CollectionRepresentationFactory.addSpecialProperties(
                                        nrep, TYPE.FILES_BUCKET, d);
                            }

                            rep.addChild("rh:bucket", nrep);
                        } else if (_SCHEMAS.equals(id.getValue())) {
                            if (request.isFullHalMode()) {
                                CollectionRepresentationFactory.addSpecialProperties(
                                        nrep, TYPE.SCHEMA_STORE, d);
                            }

                            rep.addChild("rh:schema-store", nrep);
                        } else {
                            if (request.isFullHalMode()) {
                                CollectionRepresentationFactory.addSpecialProperties(
                                        nrep, TYPE.COLLECTION, d);
                            }

                            rep.addChild("rh:coll", nrep);
                        }
                    } else {
                        // this shoudn't be possible
                        LOGGER.error("Collection missing string _id field: {}", _id);
                    }
                });
    }
}
