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
package org.restheart.handlers.indexes;

import io.undertow.server.HttpServerExchange;
import static java.lang.Math.toIntExact;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.Configuration;
import org.restheart.representation.Link;
import org.restheart.representation.Resource;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class IndexesRepresentationFactory {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(IndexesRepresentationFactory.class);

    /**
     *
     * @param exchange
     * @param context
     * @param embeddedData
     * @param size
     * @throws IllegalQueryParamenterException
     */
    static public Resource getRepresentation(
            HttpServerExchange exchange,
            RequestContext context,
            List<BsonDocument> embeddedData,
            long size)
            throws IllegalQueryParamenterException {
        String requestPath = URLUtils.removeTrailingSlashes(
                context.getUnmappedRequestUri());

        String queryString = exchange.getQueryString() == null
                || exchange.getQueryString().isEmpty()
                        ? ""
                        : "?" + URLUtils.decodeQueryString(exchange.getQueryString());

        Resource rep;

        if (context.isFullHalMode()) {
            rep = new Resource(requestPath + queryString);
        } else {
            rep = new Resource();
        }

        if (size >= 0) {
            rep.addProperty("_size", new BsonInt32(toIntExact(size)));
        }

        if (embeddedData != null) {
            long count = embeddedData.stream()
                    .filter((props) -> props.keySet().stream()
                            .anyMatch((k) -> k.equals("id") || k.equals("_id")))
                    .count();

            rep.addProperty("_returned", new BsonInt32(toIntExact(count)));

            if (!embeddedData.isEmpty()) {
                embeddedDocuments(
                        embeddedData,
                        requestPath,
                        rep,
                        context.isFullHalMode());
            }
        }

        if (context.isFullHalMode()) {
            rep.addProperty("_type",
                    new BsonString(context.getType().name()));

            if (context.isParentAccessible()) {
                // this can happen due to mongo-mounts mapped URL
                if (context.getCollectionName().endsWith(
                        RequestContext.FS_FILES_SUFFIX)) {
                    rep.addLink(new Link(
                            "rh:bucket",
                            URLUtils.getParentPath(requestPath)));
                } else {
                    rep.addLink(new Link(
                            "rh:coll",
                            URLUtils.getParentPath(requestPath)));
                }
            }

            rep.addLink(new Link("rh:indexes", requestPath));
        }

        return rep;
    }

    private static void embeddedDocuments(
            List<BsonDocument> embeddedData,
            String requestPath,
            Resource rep,
            boolean isHal) {
        embeddedData.stream().forEach((d) -> {
            BsonValue _id = d.get("_id");

            if (_id != null
                    && (_id.isString()
                    || _id.isObjectId())) {
                Resource nrep = new Resource();

                if (isHal) {
                    nrep.addProperty("_type",
                            new BsonString(RequestContext.TYPE.INDEX.name()));
                }

                nrep.addProperties(d);

                rep.addChild("rh:index", nrep);
            } else {
                rep.addWarning("index with _id "
                        + _id
                        + (_id == null
                                ? " "
                                : " of type "
                                + _id.getBsonType().name())
                        + "filtered out. Indexes can only "
                        + "have ids of type String");

                LOGGER.debug("index missing string _id field", d);
            }
        });
    }

    private IndexesRepresentationFactory() {
    }
}
