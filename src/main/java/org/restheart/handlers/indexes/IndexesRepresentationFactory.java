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
package org.restheart.handlers.indexes;

import com.mongodb.DBObject;
import org.restheart.Configuration;
import org.restheart.hal.Link;
import org.restheart.hal.Representation;
import static org.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.util.List;
import org.bson.types.ObjectId;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class IndexesRepresentationFactory {

    private static final Logger logger = LoggerFactory.getLogger(IndexesRepresentationFactory.class);

    /**
     *
     * @param exchange
     * @param context
     * @param embeddedData
     * @param size
     * @throws IllegalQueryParamenterException
     */
    static public void sendHal(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException {
        String requestPath = URLUtils.removeTrailingSlashes(context.getMappedRequestUri());
        String queryString = exchange.getQueryString() == null || exchange.getQueryString().isEmpty() 
                ? "" 
                : "?" + URLUtils.decodeQueryString(exchange.getQueryString());

        Representation rep = new Representation(requestPath + queryString);

        rep.addProperty("_type", context.getType().name());

        if (size >= 0) {
            rep.addProperty("_size", size);
        }

        if (embeddedData != null) {
            long count = embeddedData.stream()
                    .filter((props) -> props.keySet().stream()
                            .anyMatch((k) -> k.equals("id") || k.equals("_id")))
                    .count();

            rep.addProperty("_returned", count);

            if (!embeddedData.isEmpty()) {
                embeddedDocuments(embeddedData, requestPath, rep);
            }
        }

        // link templates and curies
        if (context.isParentAccessible()) {
            // this can happen due to mongo-mounts mapped URL
            rep.addLink(new Link("rh:coll", URLUtils.getParentPath(requestPath)));
        }
        rep.addLink(new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL 
                + "/#api-indexes-{rel}", false), true);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }

    private static void embeddedDocuments(List<DBObject> embeddedData, String requestPath, Representation rep) {
        embeddedData.stream().forEach((d) -> {
            Object _id = d.get("_id");
            
            if (_id != null && (_id instanceof String || _id instanceof ObjectId)) {
                Representation nrep = new Representation(requestPath + "/" + _id.toString());
                
                nrep.addProperty("_type", RequestContext.TYPE.INDEX.name());
                
                nrep.addProperties(d);
                
                rep.addRepresentation("rh:index", nrep);
            } else {
                rep.addWarning("index with _id " + _id + (_id == null ? " " :  " of type " + _id.getClass().getSimpleName()) + "filtered out. Indexes can only have ids of type String");
                logger.debug("index missing string _id field", d);
            }
        });
    }
}
