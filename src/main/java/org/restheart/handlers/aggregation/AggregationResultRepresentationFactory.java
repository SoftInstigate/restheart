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
package org.restheart.handlers.aggregation;

import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.restheart.Configuration;
import org.restheart.hal.AbstractRepresentationFactory;
import org.restheart.hal.Link;
import org.restheart.hal.Representation;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.HAL_MODE;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class AggregationResultRepresentationFactory
        extends AbstractRepresentationFactory {

    public AggregationResultRepresentationFactory() {
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
    public Representation getRepresentation(HttpServerExchange exchange,
            RequestContext context,
            List<DBObject> embeddedData,
            long size)
            throws IllegalQueryParamenterException {
        final String requestPath = buildRequestPath(exchange);
        final Representation rep
                = createRepresentation(exchange, context, requestPath);

        addSizeAndTotalPagesProperties(size, context, rep);

        addEmbeddedData(embeddedData, rep);

        if (context.getHalMode() == HAL_MODE.FULL
                || context.getHalMode() == HAL_MODE.F) {

            addPaginationLinks(exchange, context, size, rep);

            addLinkTemplates(rep, requestPath);

            // curies
            rep.addLink(new Link("rh", "curies",
                    Configuration.RESTHEART_ONLINE_DOC_URL
                    + "/{rel}.html", true), true);
        } else {
            // empty curies section. this is needed due to HAL browser issue
            // https://github.com/mikekelly/hal-browser/issues/71
            rep.addLinkArray("curies");
        }

        return rep;
    }

    private void addEmbeddedData(List<DBObject> embeddedData,
            final Representation rep)
            throws IllegalQueryParamenterException {
        if (embeddedData != null) {
            addReturnedProperty(embeddedData, rep);

            if (!embeddedData.isEmpty()) {
                embeddedDocuments(embeddedData, rep);
            }
        } else {
            rep.addProperty("_returned", 0);
        }
    }

    private void addLinkTemplates(final Representation rep,
            final String requestPath) {
        rep.addLink(new Link("rh:collection",
                URLUtils.getParentPath(URLUtils.getParentPath(requestPath))));
        rep.addLink(new Link("rh:paging",
                requestPath + "{?page}{&pagesize}", true));
    }

    private void embeddedDocuments(List<DBObject> embeddedData,
            Representation rep) throws IllegalQueryParamenterException {
        for (DBObject d : embeddedData) {
            Representation nrep = new Representation();

            nrep.addProperties(d);

            rep.addRepresentation("rh:result", nrep);
        }
    }
}
