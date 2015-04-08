/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.handlers.root;

import com.mongodb.DBObject;
import org.restheart.Configuration;
import org.restheart.hal.Link;
import org.restheart.hal.Representation;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.bson.types.ObjectId;
import org.restheart.hal.AbstractRepresentationFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RootRepresentationFactory extends AbstractRepresentationFactory {

    private static final Logger logger = LoggerFactory.getLogger(RootRepresentationFactory.class);

    public RootRepresentationFactory() {
    }

    @Override
    public Representation getRepresentation(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException {
        final String requestPath = buildRequestPath(exchange);
        final Representation rep = createRepresentation(exchange, context, requestPath);

        addSizeAndTotalPagesProperties(size, context, rep);

        addEmbeddedData(embeddedData, rep, requestPath);

        addPaginationLinks(exchange, context, size, rep);

        addLinkTemplatesAndCuries(exchange, context, rep, requestPath);

        return rep;
    }

    private void addEmbeddedData(List<DBObject> embeddedData, final Representation rep, final String requestPath) {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);
            if (!embeddedData.isEmpty()) {
                embeddedDocuments(embeddedData, hasTrailingSlash(requestPath), requestPath, rep);
            }
        }
    }

    private void addLinkTemplatesAndCuries(final HttpServerExchange exchange, final RequestContext context, final Representation rep, final String requestPath) {
        //curies
        rep.addLink(new Link("rh:paging", requestPath + "{?page}{&pagesize}", true));
        rep.addLink(new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL + "/#api-root-{rel}", true), true);
    }

    private void embeddedDocuments(List<DBObject> embeddedData, boolean trailingSlash, String requestPath, Representation rep) {
        embeddedData.stream().forEach((d) -> {
            Object _id = d.get("_id");

            if (_id != null && (_id instanceof String || _id instanceof ObjectId)) {
                Representation nrep;

                if (trailingSlash) {
                    nrep = new Representation(requestPath + _id.toString());
                } else {
                    nrep = new Representation(requestPath + "/" + _id.toString());
                }

                nrep.addProperty("_type", RequestContext.TYPE.DB.name());

                nrep.addProperties(d);

                rep.addRepresentation("rh:db", nrep);
            } else {
                // this shoudn't be possible
                logger.error("db missing string _id field", d);
            }
        });
    }
}
