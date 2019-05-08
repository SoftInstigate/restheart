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
package org.restheart.handlers.database;

import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.Configuration;
import org.restheart.representation.AbstractRepresentationFactory;
import org.restheart.representation.Link;
import org.restheart.representation.Resource;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.TYPE;
import org.restheart.handlers.collection.CollectionRepresentationFactory;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DBRepresentationFactory extends AbstractRepresentationFactory {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(DBRepresentationFactory.class);

    public static void addSpecialProperties(
            final Resource rep,
            RequestContext.TYPE type,
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

    public DBRepresentationFactory() {
    }

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

        addEmbeddedData(embeddedData, context, rep, requestPath);

        if (context.isFullHalMode()) {

            addPaginationLinks(exchange, context, size, rep);

            addSpecialProperties(rep, context.getType(), context.getDbProps());

            addLinkTemplates(context, rep, requestPath);
        }

        return rep;
    }

    private void addProperties(
            final Resource rep,
            RequestContext context) {
        final BsonDocument dbProps = context.getDbProps();

        rep.addProperties(dbProps);
    }

    private void addEmbeddedData(
            final List<BsonDocument> embeddedData,
            final RequestContext context,
            final Resource rep,
            final String requestPath) {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);

            if (!embeddedData.isEmpty()) {
                embeddedCollections(embeddedData, context, requestPath, rep);
            }
        } else {
            rep.addProperty("_returned", new BsonInt32(0));
        }
    }

    private void addLinkTemplates(
            final RequestContext context,
            final Resource rep,
            final String requestPath) {
        String parentPath = URLUtils.getParentPath(requestPath);

        // link templates and curies
        if (context.isParentAccessible()) {
            // this can happen due to mongo-mounts mapped URL
            rep.addLink(new Link("rh:root", parentPath));
        }

        if (parentPath.endsWith("/")) {
            rep.addLink(new Link("rh:db", URLUtils.removeTrailingSlashes(
                    URLUtils.getParentPath(requestPath)) + "{dbname}", true));
        } else {
            rep.addLink(new Link("rh:db", URLUtils.removeTrailingSlashes(
                    URLUtils.getParentPath(requestPath)) + "/{dbname}", true));
        }

        rep.addLink(new Link("rh:coll", requestPath + "/{collname}", true));
        rep.addLink(new Link("rh:bucket", requestPath
                + "/{bucketname}"
                + RequestContext.FS_FILES_SUFFIX, true));

        rep.addLink(new Link("rh:paging", requestPath
                + "{?page}{&pagesize}", true));
    }

    private void embeddedCollections(
            final List<BsonDocument> embeddedData,
            final RequestContext context,
            final String requestPath,
            final Resource rep) {
        embeddedData.stream().forEach((d) -> {
            BsonValue _id = d.get("_id");

            if (_id != null && _id.isString()) {
                BsonString id = _id.asString();

                // avoid starting double slash in self href for root URI
                String rp = URLUtils.removeTrailingSlashes(requestPath);
                rp = "/".equals(rp) ? "" : rp;

                final Resource nrep;

                if (context.isFullHalMode()) {
                    nrep = new Resource(rp + "/" + id.getValue());
                } else {
                    nrep = new Resource();
                }

                nrep.addProperties(d);

                if (id.getValue().endsWith(RequestContext.FS_FILES_SUFFIX)) {
                    if (context.isFullHalMode()) {
                        CollectionRepresentationFactory.addSpecialProperties(
                                nrep, TYPE.FILES_BUCKET, d);
                    }

                    rep.addChild("rh:bucket", nrep);
                } else if (RequestContext._SCHEMAS.equals(id)) {
                    if (context.isFullHalMode()) {
                        CollectionRepresentationFactory.addSpecialProperties(
                                nrep, TYPE.SCHEMA_STORE, d);
                    }

                    rep.addChild("rh:schema-store", nrep);
                } else {
                    if (context.isFullHalMode()) {
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
