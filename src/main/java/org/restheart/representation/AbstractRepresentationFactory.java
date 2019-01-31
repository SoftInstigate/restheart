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
package org.restheart.representation;

import io.undertow.server.HttpServerExchange;
import static java.lang.Math.toIntExact;
import java.util.List;
import java.util.TreeMap;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public abstract class AbstractRepresentationFactory {

    /**
     *
     * @param exchange
     * @param context
     * @param embeddedData
     * @param size
     * @return the resource HAL representation
     * @throws IllegalQueryParamenterException
     */
    public abstract Resource getRepresentation(
            HttpServerExchange exchange,
            RequestContext context,
            List<BsonDocument> embeddedData,
            long size)
            throws IllegalQueryParamenterException;

    protected void addSizeAndTotalPagesProperties(
            final long size,
            final RequestContext context,
            final Resource rep) {
        if (size == 0) {
            rep.addProperty("_size", new BsonInt32(0));

            if (context.getPagesize() > 0) {
                rep.addProperty("_total_pages", new BsonInt32(0));
            }
        }

        if (size > 0) {
            float _size = size + 0f;
            float _pagesize = context.getPagesize() + 0f;

            rep.addProperty("_size", new BsonInt32(toIntExact(size)));

            if (context.getPagesize() > 0) {
                rep.addProperty("_total_pages", new BsonInt32(
                        toIntExact(
                                Math.max(1,
                                        Math.round(
                                                Math.ceil(_size / _pagesize)
                                        )))));
            }
        }
    }

    protected void addReturnedProperty(
            final List<BsonDocument> embeddedData,
            final Resource rep) {
        long count = embeddedData == null ? 0 : embeddedData.size();

        rep.addProperty("_returned", new BsonInt32(toIntExact(count)));
    }

    protected Resource createRepresentation(
            final HttpServerExchange exchange,
            final RequestContext context,
            final String requestPath) {
        String queryString
                = exchange.getQueryString() == null
                || exchange.getQueryString().isEmpty()
                ? ""
                : "?" + URLUtils.decodeQueryString(
                        exchange.getQueryString());

        Resource rep;

        if (requestPath != null || context.isFullHalMode()) {
            rep = new Resource(requestPath + queryString);
        } else {
            rep = new Resource();
        }

        return rep;
    }

    protected String buildRequestPath(final HttpServerExchange exchange) {
        String requestPath = URLUtils.removeTrailingSlashes(
                exchange.getRequestPath());
        return requestPath;
    }

    protected void addPaginationLinks(
            HttpServerExchange exchange,
            RequestContext context,
            long size,
            final Resource rep)
            throws IllegalQueryParamenterException {
        if (context.getPagesize() > 0) {
            TreeMap<String, String> links;
            links = RepUtils.getPaginationLinks(exchange, context, size);
            if (links != null) {
                links.keySet().stream().forEach((k) -> {
                    rep.addLink(new Link(k, links.get(k)));
                });
            }
        }
    }

    protected boolean hasTrailingSlash(final String requestPath) {
        return requestPath.substring(requestPath.length() > 0
                ? requestPath.length() - 1
                : 0).equals("/");
    }
}
