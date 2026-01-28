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
package org.restheart.mongodb.hal;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonArray;
import org.bson.BsonInt32;
import org.restheart.exchange.IllegalQueryParameterException;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.utils.MongoURLUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class AggregationResultRepresentationFactory
        extends AbstractRepresentationFactory {

    /**
     *
     */
    public AggregationResultRepresentationFactory() {
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
    public Resource getRepresentation(HttpServerExchange exchange,
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

        addSize(size, rep);

        addEmbeddedData(embeddedData, rep);

        if (request.isFullHalMode()) {
            addLinkTemplates(rep, requestPath);
        }

        return rep;
    }

    private void addEmbeddedData(BsonArray embeddedData,
            final Resource rep)
            throws IllegalQueryParameterException {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);

            if (!embeddedData.isEmpty()) {
                embeddedDocuments(embeddedData, rep);
            }
        } else {
            rep.addProperty("_returned", new BsonInt32(0));
        }
    }

    private void addLinkTemplates(final Resource rep,
            final String requestPath) {
        rep.addLink(new Link("rh:collection",
                MongoURLUtils.getParentPath(MongoURLUtils.getParentPath(requestPath))));
    }

    private void embeddedDocuments(BsonArray embeddedData,
            Resource rep) throws IllegalQueryParameterException {
        embeddedData.stream()
                .filter(d -> d != null)
                .filter(d -> d.isDocument())
                .map(d -> d.asDocument())
                .map((d) -> {
                    Resource nrep = new Resource();
                    nrep.addProperties(d);
                    return nrep;
                }).forEach((nrep) -> {
            rep.addChild("rh:result", nrep);
        });
    }

    /**
     *
     * @param size
     * @param rep
     */
    protected void addSize(
            final long size,
            final Resource rep) {
        if (size == 0) {
            rep.addProperty("_size", new BsonInt32(0));
        }
    }
}
