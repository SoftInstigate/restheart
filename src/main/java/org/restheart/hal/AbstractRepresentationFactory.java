/*
 * RESTHeart - the data REST API server
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
package org.restheart.hal;

import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.util.List;
import java.util.TreeMap;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import static org.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public abstract class AbstractRepresentationFactory {
    /**
     *
     * @param exchange
     * @param context
     * @param rep
     */
    public void sendRepresentation(HttpServerExchange exchange, RequestContext context, Representation rep) {
        if (context.getWarnings() != null)
            context.getWarnings().forEach(w -> rep.addWarning(w));
        
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }

    /**
     *
     * @param exchange
     * @param context
     * @param embeddedData
     * @param size
     * @return the resource HAL representation
     * @throws IllegalQueryParamenterException
     */
    public abstract Representation getRepresentation(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException;

    protected void addSizeAndTotalPagesProperties(final long size, final RequestContext context, final Representation rep) {
        if (size >= 0) {
            float _size = size + 0f;
            float _pagesize = context.getPagesize() + 0f;

            rep.addProperty("_size", size);
            rep.addProperty("_total_pages", Math.max(1, Math.round(Math.ceil(_size / _pagesize))));
        }
    }

    protected void addReturnedProperty(final List<DBObject> embeddedData, final Representation rep) {
        long count = embeddedData == null ? 0 : embeddedData.stream()
                .filter((props) -> props.keySet().stream()
                        .anyMatch((k) -> k.equals("id") || k.equals("_id")))
                .count();
        rep.addProperty("_returned", count);
    }

    protected Representation createRepresentation(final HttpServerExchange exchange, final RequestContext context, final String requestPath) {
        String queryString = exchange.getQueryString() == null || exchange.getQueryString().isEmpty()
                ? ""
                : "?" + URLUtils.decodeQueryString(exchange.getQueryString());
        Representation rep = new Representation(requestPath + queryString);
        rep.addProperty("_type", context.getType().name());
        return rep;
    }

    protected String buildRequestPath(final HttpServerExchange exchange) {
        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());
        return requestPath;
    }

    protected void addPaginationLinks(HttpServerExchange exchange, RequestContext context, long size, final Representation rep) throws IllegalQueryParamenterException {
        TreeMap<String, String> links;
        links = HALUtils.getPaginationLinks(exchange, context, size);
        if (links != null) {
            links.keySet().stream().forEach((k) -> {
                rep.addLink(new Link(k, links.get(k)));
            });
        }
    }

    protected boolean hasTrailingSlash(final String requestPath) {
        return requestPath.substring(requestPath.length() > 0 ? requestPath.length() - 1 : 0).equals("/");
    }
}