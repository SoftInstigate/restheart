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
package org.restheart.mongodb.handlers.root;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.mongodb.Version;
import org.restheart.mongodb.handlers.IllegalQueryParamenterException;
import org.restheart.mongodb.handlers.database.DBRepresentationFactory;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.ExchangeKeys.TYPE;
import org.restheart.mongodb.representation.AbstractRepresentationFactory;
import org.restheart.mongodb.representation.Link;
import org.restheart.mongodb.representation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RootRepresentationFactory extends AbstractRepresentationFactory {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(RootRepresentationFactory.class);

    /**
     *
     */
    public RootRepresentationFactory() {
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
            List<BsonDocument> embeddedData,
            long size)
            throws IllegalQueryParamenterException {
        var request = BsonRequest.wrap(exchange);
        
        final String requestPath = buildRequestPath(exchange);
        final Resource rep;

        if (request.isFullHalMode()) {
            rep = createRepresentation(exchange, requestPath);
        } else {
            rep = createRepresentation(exchange, null);
        }

        addSizeAndTotalPagesProperties(size, request, rep);

        addEmbeddedData(request, embeddedData, rep, requestPath);

        if (request.isFullHalMode()) {
            addSpecialProperties(rep, request);

            addPaginationLinks(exchange, size, rep);

            addLinkTemplates(rep, requestPath);
        }

        return rep;
    }

    private void addSpecialProperties(
            final Resource rep,
            BsonRequest request) {
        if (Version.getInstance().getVersion() == null) {
            rep.addProperty("_restheart_version",
                    new BsonString("unknown, not packaged"));
        } else {
            rep.addProperty("_restheart_version",
                    new BsonString(Version.getInstance().getVersion()));
        }

        rep.addProperty("_type", new BsonString(request.getType().name()));
    }

    private void addEmbeddedData(
            BsonRequest request,
            List<BsonDocument> embeddedData,
            final Resource rep,
            final String requestPath) {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);
            if (!embeddedData.isEmpty()) {
                embeddedDbs(
                        request,
                        embeddedData,
                        hasTrailingSlash(requestPath),
                        requestPath,
                        rep);
            }
        }
    }

    private void addLinkTemplates(
            final Resource rep,
            final String requestPath) {
        rep.addLink(new Link("rh:root", requestPath));
        rep.addLink(new Link("rh:db", requestPath + "{dbname}", true));

        rep.addLink(new Link("rh:paging",
                requestPath + "{?page}{&pagesize}", true));
    }

    private void embeddedDbs(
            BsonRequest request,
            List<BsonDocument> embeddedData,
            boolean trailingSlash,
            String requestPath,
            Resource rep) {
        embeddedData.stream().filter(d -> d != null).forEach((d) -> {
            BsonValue _id = d.get("_id");

            if (_id != null
                    && _id.isString()) {
                final Resource nrep;

                if (request.isFullHalMode()) {
                    if (trailingSlash) {
                        nrep = new Resource(requestPath
                                + _id.asString().getValue());
                    } else {
                        nrep = new Resource(requestPath
                                + "/"
                                + _id.asString().getValue());
                    }

                    DBRepresentationFactory.addSpecialProperties(
                            nrep,
                            TYPE.DB,
                            d);
                } else {
                    nrep = new Resource();
                }

                nrep.addProperties(d);

                rep.addChild("rh:db", nrep);
            } else {
                // this shoudn't be possible
                LOGGER.error("db missing string _id field", d);
            }
        });
    }
}
