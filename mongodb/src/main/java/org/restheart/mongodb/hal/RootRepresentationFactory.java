/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
import org.bson.BsonArray;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.exchange.IllegalQueryParameterException;
import org.restheart.exchange.MongoRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class RootRepresentationFactory extends AbstractRepresentationFactory {

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
     * @throws IllegalQueryParameterException
     */
    @Override
    public Resource getRepresentation(
            HttpServerExchange exchange,
            BsonArray embeddedData,
            long size)
            throws IllegalQueryParameterException {
        var request = MongoRequest.of(exchange);

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
            MongoRequest request) {
        rep.addProperty("_type", new BsonString(request.getType().name()));
    }

    private void addEmbeddedData(
            MongoRequest request,
            BsonArray embeddedData,
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
            MongoRequest request,
            BsonArray embeddedData,
            boolean trailingSlash,
            String requestPath,
            Resource rep) {
        embeddedData.stream()
                .filter(d -> d != null)
                .filter(d -> d.isDocument())
                .map(d -> d.asDocument())
                .forEach((d) -> {
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
