/*
 * RESTHeart - the Web API for MongoDB
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
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.bson.types.ObjectId;
import org.restheart.Configuration;
import static org.restheart.Configuration.RESTHEART_VERSION;
import org.restheart.hal.AbstractRepresentationFactory;
import org.restheart.hal.Link;
import org.restheart.hal.Representation;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.database.DBRepresentationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RootRepresentationFactory extends AbstractRepresentationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootRepresentationFactory.class);

    public RootRepresentationFactory() {
    }

    @Override
    public Representation getRepresentation(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException {
        final String requestPath = buildRequestPath(exchange);
        final Representation rep;

        if (context.isFullHalMode()) {
            rep = createRepresentation(exchange, context, requestPath);
        } else {
            rep = createRepresentation(exchange, context, null);
        }

        addSizeAndTotalPagesProperties(size, context, rep);

        addEmbeddedData(context, embeddedData, rep, requestPath);

        if (context.isFullHalMode()) {
            addSpecialProperties(rep, context);

            addPaginationLinks(exchange, context, size, rep);

            addLinkTemplates(rep, requestPath);

            //curies
            rep.addLink(new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL
                    + "/{rel}.html", true), true);
        } 

        return rep;
    }

    private void addSpecialProperties(final Representation rep, RequestContext context) {
        if (RESTHEART_VERSION == null) {
            rep.addProperty("_restheart_version", "unknown, not packaged");
        } else {
            rep.addProperty("_restheart_version", RESTHEART_VERSION);
        }

        rep.addProperty("_type", context.getType().name());
    }

    private void addEmbeddedData(RequestContext context, List<DBObject> embeddedData, final Representation rep, final String requestPath) {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);
            if (!embeddedData.isEmpty()) {
                embeddedDbs(context, embeddedData, hasTrailingSlash(requestPath), requestPath, rep);
            }
        }
    }

    private void addLinkTemplates(final Representation rep, final String requestPath) {
        rep.addLink(new Link("rh:root", requestPath));
        rep.addLink(new Link("rh:db", requestPath + "{dbname}", true));

        rep.addLink(new Link("rh:paging", requestPath + "{?page}{&pagesize}", true));
    }

    private void embeddedDbs(RequestContext context, List<DBObject> embeddedData, boolean trailingSlash, String requestPath, Representation rep) {
        embeddedData.stream().filter(d -> d != null).forEach((d) -> {
            Object _id = d.get("_id");

            if (_id != null && (_id instanceof String || _id instanceof ObjectId)) {
                final Representation nrep;

                if (context.isFullHalMode()) {
                    if (trailingSlash) {
                        nrep = new Representation(requestPath + _id.toString());
                    } else {
                        nrep = new Representation(requestPath + "/" + _id.toString());
                    }

                    DBRepresentationFactory.addSpecialProperties(nrep, RequestContext.TYPE.DB, d);
                } else {
                    nrep = new Representation();
                }

                nrep.addProperties(d);

                rep.addRepresentation("rh:db", nrep);
            } else {
                // this shoudn't be possible
                LOGGER.error("db missing string _id field", d);
            }
        });
    }
}
