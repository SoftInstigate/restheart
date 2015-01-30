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
package org.restheart.handlers.document;

import org.restheart.hal.Link;
import org.restheart.hal.Representation;
import com.mongodb.DBObject;
import org.restheart.Configuration;
import static org.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.Relationship;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.net.URISyntaxException;
import java.util.List;
import java.util.TreeMap;
import org.bson.types.ObjectId;
import org.restheart.handlers.RequestContext.DOC_ID_TYPE;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DocumentRepresentationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRepresentationFactory.class);

    /**
     *
     * @param href
     * @param exchange
     * @param context
     * @param data
     * @return
     * @throws IllegalQueryParamenterException
     */
    public static Representation getDocument(String href, HttpServerExchange exchange, RequestContext context, DBObject data)
            throws IllegalQueryParamenterException {
        Representation rep;
        
        Object id = data.get("_id");
        
        if (id == null) {
            rep = new Representation("#");
        } else if (id instanceof String || id instanceof ObjectId) {
            rep = new Representation(href);
        }else if (id instanceof Integer) {
            rep = new Representation(href + "?doc_id_type=" + DOC_ID_TYPE.INT);
        } else if (id instanceof Long) {
            rep = new Representation(href + "?doc_id_type=" + DOC_ID_TYPE.LONG);
        } else if (id instanceof Float) {
            rep = new Representation(href + "?doc_id_type=" + DOC_ID_TYPE.FLOAT);
        } else if (id instanceof Double) {
            rep = new Representation(href + "?doc_id_type=" + DOC_ID_TYPE.DOUBLE);
        } else {
            rep = new Representation("#");
            rep.addWarning("this resource does not have an URI since the _id is of type " + id.getClass().getSimpleName());
        }
        

        rep.addProperty("_type", context.getType().name());

        // document properties
        data.keySet().stream().forEach((key) -> rep.addProperty(key, data.get(key)));

        // document links
        TreeMap<String, String> links;

        links = getRelationshipsLinks(rep, context, data);

        if (links != null) {
            links.keySet().stream().forEach((k) -> {
                rep.addLink(new Link(k, links.get(k)));
            });
        }

        // link templates and curies
        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());
        if (context.isParentAccessible()) {
            // this can happen due to mongo-mounts mapped URL
            rep.addLink(new Link("rh:coll", URLUtils.getParentPath(requestPath)));
        }
        rep.addLink(new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL + "/#api-doc-{rel}", false), true);

        return rep;
    }

    /**
     *
     * @param href
     * @param exchange
     * @param context
     * @param data
     * @throws IllegalQueryParamenterException
     * @throws URISyntaxException
     */
    public static void sendDocument(String href, HttpServerExchange exchange, RequestContext context, DBObject data)
            throws IllegalQueryParamenterException, URISyntaxException {
        Representation rep = getDocument(href, exchange, context, data);
        
        if (context.getWarnings() != null)
            context.getWarnings().forEach(w -> rep.addWarning(w));

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }

    private static TreeMap<String, String> getRelationshipsLinks(Representation rep, RequestContext context, DBObject data) {
        TreeMap<String, String> links = new TreeMap<>();

        List<Relationship> rels = null;

        try {
            rels = Relationship.getFromJson((DBObject) context.getCollectionProps());
        } catch (InvalidMetadataException ex) {
            rep.addWarning("collection " + context.getDBName() 
                    + "/" + context.getCollectionName()
                    + " has invalid relationships definition");
        }

        if (rels == null) {
            return links;
        }

        for (Relationship rel : rels) {
            try {
                String link = rel.getRelationshipLink(context, context.getDBName(), context.getCollectionName(), data);

                if (link != null) {
                    links.put(rel.getRel(), link);
                }
            } catch (IllegalArgumentException ex) {
                rep.addWarning(ex.getMessage());
                LOGGER.debug(ex.getMessage(), ex);
            }
        }

        return links;
    }
}
