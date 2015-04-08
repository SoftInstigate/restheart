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
import java.util.List;
import java.util.TreeMap;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DocumentRepresentationFactory {
    
    public DocumentRepresentationFactory() {
        
    }

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
    public Representation getRepresentation(String href, HttpServerExchange exchange, RequestContext context, DBObject data)
            throws IllegalQueryParamenterException {
        Representation rep;

        Object id = data.get("_id");

        String _docIdType = null;

        rep = new Representation(URLUtils.getReferenceLink(context, URLUtils.getParentPath(href), id));

        rep.addProperty(
                "_type", context.getType().name());

        // document properties
        data.keySet()
                .stream().forEach((key) -> rep.addProperty(key, data.get(key)));

        // document links
        TreeMap<String, String> links;

        links = getRelationshipsLinks(rep, context, data);

        if (links
                != null) {
            links.keySet().stream().forEach((k) -> {
                rep.addLink(new Link(k, links.get(k)));
            });
        }

        // link templates and curies
        String requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());

        if (isBinaryFile(data)) {
            if (_docIdType == null) {
                rep.addLink(new Link("rh:data",
                        String.format("%s/%s", href, RequestContext.BINARY_CONTENT)));
            } else {
                rep.addLink(new Link("rh:data",
                        String.format("%s/%s?%s", href, RequestContext.BINARY_CONTENT, _docIdType)));
            }
            
            if (context.isParentAccessible()) {
                rep.addLink(new Link("rh:bucket", URLUtils.getParentPath(requestPath)));
            }
        } else {
            if (context.isParentAccessible()) {
                rep.addLink(new Link("rh:coll", URLUtils.getParentPath(requestPath)));
            }
        }

        rep.addLink(
                new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL + "/#api-doc-{rel}", false), true);

        return rep;
    }

    private static boolean isBinaryFile(DBObject data) {
        return data.containsField("filename") && data.containsField("chunkSize");
    }

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
            } catch (IllegalArgumentException | UnsupportedDocumentIdException ex) {
                rep.addWarning(ex.getMessage());
                LOGGER.debug(ex.getMessage(), ex);
            }
        }

        return links;
    }
}
