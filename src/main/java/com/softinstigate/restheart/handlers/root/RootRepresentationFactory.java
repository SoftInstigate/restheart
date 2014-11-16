/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package com.softinstigate.restheart.handlers.root;

import com.softinstigate.restheart.hal.*;
import com.mongodb.DBObject;
import com.softinstigate.restheart.Configuration;
import static com.softinstigate.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import com.softinstigate.restheart.utils.URLUtilis;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.net.URISyntaxException;
import java.util.List;
import java.util.TreeMap;
import org.bson.types.ObjectId;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare
 */
public class RootRepresentationFactory {
    private static final Logger logger = LoggerFactory.getLogger(RootRepresentationFactory.class);

    /**
     *
     * @param exchange
     * @param context
     * @param embeddedData
     * @param size
     * @throws IllegalQueryParamenterException
     * @throws URISyntaxException
     */
    public static void sendHal(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException, URISyntaxException {
        Representation rep = getDbs(exchange, context, embeddedData, size);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }

    static private Representation getDbs(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException {
        String requestPath = URLUtilis.removeTrailingSlashes(exchange.getRequestPath());
        String queryString = (exchange.getQueryString() == null || exchange.getQueryString().isEmpty()) ? "" : "?" + exchange.getQueryString();

        boolean trailingSlash = requestPath.substring(requestPath.length() > 0 ? requestPath.length() - 1 : 0).equals("/");

        Representation rep = new Representation(requestPath + queryString);

        rep.addProperty("_type", context.getType().name());

        if (size >= 0) {
            float _size = size + 0f;
            float _pagesize = context.getPagesize() + 0f;

            rep.addProperty("_size", size);
            rep.addProperty("_total_pages", Math.max(1, Math.round(Math.ceil(_size / _pagesize))));
        }

        if (embeddedData != null) {
            long count = embeddedData.stream().filter((props) -> props.keySet().stream().anyMatch((k) -> k.equals("id") || k.equals("_id"))).count();

            rep.addProperty("_returned", count);

            if (!embeddedData.isEmpty()) // embedded documents
            {
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

                        if (d.get("_etag") != null && d.get("_etag") instanceof ObjectId) {
                            d.put("_etag", ((ObjectId) d.get("_etag")).toString()); // represent the etag as a string
                        }
                        nrep.addProperties(d);

                        rep.addRepresentation("rh:db", nrep);
                    } else {
                        logger.error("db missing string _id field", d);
                    }
                });
            }
        }

        // collection links
        TreeMap<String, String> links;

        links = HALUtils.getPaginationLinks(exchange, context, size);

        if (links != null) {
            links.keySet().stream().forEach((k) -> {
                rep.addLink(new Link(k, links.get(k)));
            });
        }

        //curies
        rep.addLink(new Link("rh:paging", requestPath + "{?page}{&pagesize}", true));
        rep.addLink(new Link("rh", "curies", Configuration.DOC_URL + "/#api/root/{rel}", true), true);

        ResponseHelper.injectWarnings(rep, exchange, context);

        return rep;
    }
}
