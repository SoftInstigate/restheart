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
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtilis;
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
public class DocumentRepresentationFactory {

    private static final Logger logger = LoggerFactory.getLogger(DocumentRepresentationFactory.class);

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
        Representation rep = new Representation(href);

        rep.addProperty("_type", context.getType().name());

        // document properties
        data.keySet().stream().forEach((key) -> {
            Object value = data.get(key);

            if (value instanceof ObjectId) {
                value = value.toString();
            }

            rep.addProperty(key, value);
        });

        // document links
        TreeMap<String, String> links;

        links = getRelationshipsLinks(context, data);

        if (links != null) {
            links.keySet().stream().forEach((k) -> {
                rep.addLink(new Link(k, links.get(k)));
            });
        }

        // link templates and curies
        String requestPath = URLUtilis.removeTrailingSlashes(exchange.getRequestPath());
        if (context.isParentAccessible()) {
            // this can happen due to mongo-mounts mapped URL
            rep.addLink(new Link("rh:coll", URLUtilis.getPerentPath(requestPath)));
        }
        rep.addLink(new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL + "/#api-doc-{rel}", false), true);

        ResponseHelper.injectWarnings(rep, exchange, context);

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

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }

    private static TreeMap<String, String> getRelationshipsLinks(RequestContext context, DBObject data) {
        TreeMap<String, String> links = new TreeMap<>();

        List<Relationship> rels = null;

        try {
            rels = Relationship.getFromJson((DBObject) context.getCollectionProps());
        } catch (InvalidMetadataException ex) {
            context.addWarning("collection " + context.getDBName() + "/" + context.getCollectionName() + " has invalid relationships definition");
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
                context.addWarning(ex.getMessage());
                logger.warn(ex.getMessage(), ex);
            }
        }

        return links;
    }
}
