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
import com.softinstigate.restheart.hal.properties.InvalidMetadataException;
import com.mongodb.DBObject;
import static com.softinstigate.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.hal.properties.Relationship;
import com.softinstigate.restheart.utils.ResponseHelper;
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
 * @author uji
 */
public class DocumentRepresentationFactory
{
    private static final Logger logger = LoggerFactory.getLogger(DocumentRepresentationFactory.class);

    public static Representation getDocument(String href, HttpServerExchange exchange, RequestContext context, DBObject data)
            throws IllegalQueryParamenterException
    {
        Representation rep = new Representation(href);

        // document properties
        data.keySet().stream().forEach((key) ->
        {
            Object value = data.get(key);

            if (value instanceof ObjectId)
            {
                value = value.toString();
            }

            rep.addProperty(key, value);
        });
        
        // document links
        TreeMap<String, String> links;

        links = getRelationshipsLinks(context, data);

        if (links != null)
        {
            links.keySet().stream().forEach((k) ->
            {
                rep.addLink(new Link(k, links.get(k)));
            });
        }

        ResponseHelper.injectWarnings(rep, exchange, context);
        
        return rep;
    }
    
    public static void sendDocument(String href, HttpServerExchange exchange, RequestContext context, DBObject data)
            throws IllegalQueryParamenterException, URISyntaxException
    {
        Representation rep = getDocument(href, exchange, context, data);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }

    private static TreeMap<String, String> getRelationshipsLinks(RequestContext context, DBObject data)
    {
        TreeMap<String, String> links = new TreeMap<>();

        List<Relationship> rels = null;

        try
        {
            rels = Relationship.getFromJson((DBObject) context.getCollectionProps());
        }
        catch (InvalidMetadataException ex)
        {
            context.addWarning("collection " + context.getDBName() + "/" +context.getCollectionName() + " has invalid relationships definition");
            logger.error("collection {}/{} has invalid relationships definition", context.getDBName(), context.getCollectionName(), ex);
        }

        if (rels == null)
        {
            return links;
        }

        for (Relationship rel : rels)
        {
            try
            {
                String link = rel.getRelationshipLink(context.getDBName(), context.getCollectionName(), data);

                if (link != null)
                {
                    links.put(rel.getRel(), link);
                }
            }
            catch (IllegalArgumentException ex)
            {
                context.addWarning(ex.getMessage());
                logger.warn(ex.getMessage(), ex);
            }
        }

        return links;
    }
}