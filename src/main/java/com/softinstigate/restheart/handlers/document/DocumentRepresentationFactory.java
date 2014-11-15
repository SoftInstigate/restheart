/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.handlers.document;

import com.softinstigate.restheart.hal.*;
import com.mongodb.DBObject;
import com.softinstigate.restheart.Configuration;
import static com.softinstigate.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import com.softinstigate.restheart.hal.metadata.InvalidMetadataException;
import com.softinstigate.restheart.hal.metadata.Relationship;
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
        if (context.isParentAccessible()) // this can happen due to mongo-mounts mapped URL
        {
            rep.addLink(new Link("rh:coll", URLUtilis.getPerentPath(requestPath)));
        }
        rep.addLink(new Link("rh", "curies", Configuration.DOC_URL + "/#api/doc/{rel}", true), true);

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
