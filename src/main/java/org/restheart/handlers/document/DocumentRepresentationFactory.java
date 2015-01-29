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
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.net.URISyntaxException;
import java.util.List;
import java.util.TreeMap;
import org.restheart.utils.URLUtils.DOC_ID_TYPE;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DocumentRepresentationFactory {

    private static final Logger logger = LoggerFactory.getLogger(DocumentRepresentationFactory.class);

    /**
     *
     * @param href
     * @param docIdType
     * @param exchange
     * @param context
     * @param data
     * @param embedded true if this document is embedded in a parent document
     * @return
     * @throws IllegalQueryParamenterException
     */
    public static Representation getDocument(String href, DOC_ID_TYPE docIdType, HttpServerExchange exchange, RequestContext context, DBObject data, boolean embedded)
            throws IllegalQueryParamenterException {

        Representation rep;
        
        if (docIdType == DOC_ID_TYPE.STRING_OBJECTID) {
            rep = new Representation(href);
        } else {
            rep = new Representation(href + "?doc_id_type=" + docIdType.name());
        }

        rep.addProperty("_type", context.getType().name());

        // document properties
        data.keySet().stream().forEach((key) -> rep.addProperty(key, data.get(key)));

        // document links
        TreeMap<String, String> links;

        links = getRelationshipsLinks(context, data);

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

        // inject warning only on the root representation
        if (!embedded) {
            ResponseHelper.injectWarnings(rep, exchange, context);
        }

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
        Representation rep = getDocument(href, context.getDocIdType(), exchange, context, data, false);

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
