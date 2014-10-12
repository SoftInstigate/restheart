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
import com.softinstigate.restheart.hal.metadata.InvalidMetadataException;
import com.mongodb.DBObject;
import static com.softinstigate.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.hal.metadata.Relationship;
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
 * @author uji
 */
public class DocumentRepresentationFactory
{
    private static final Logger logger = LoggerFactory.getLogger(DocumentRepresentationFactory.class);

    public static Representation getDocument(HttpServerExchange exchange, RequestContext context, DBObject data)
            throws IllegalQueryParamenterException
    {
        String requestPath = URLUtilis.getRequestPath(exchange);

        Representation rep = new Representation(requestPath);

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
        TreeMap<String, String> links = null;

        try
        {
            links = getRelationshipsLinks(exchange, context, data);
        }
        catch (InvalidMetadataException ex)
        {
            logger.warn(ex.getMessage(), context.getDBName(), context.getCollectionName(), ex);
        }

        if (links != null)
        {
            for (String k : links.keySet())
            {
                rep.addLink(new Link(k, links.get(k)));
            }
        }

        return rep;
    }
    
    public static void sendDocument(HttpServerExchange exchange, RequestContext context, DBObject data)
            throws IllegalQueryParamenterException, URISyntaxException
    {
        Representation rep = getDocument(exchange, context, data);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }

    private static TreeMap<String, String> getRelationshipsLinks(HttpServerExchange exchange, RequestContext context, DBObject data) throws InvalidMetadataException
    {
        TreeMap<String, String> links = new TreeMap<>();

        List<Relationship> rels;

        try
        {
            rels = Relationship.getFromJson((DBObject) context.getCollectionProps());
        }
        catch (InvalidMetadataException ex)
        {
            logger.error("collection {}/{} has invalid relationships definition", context.getDBName(), context.getCollectionName(), ex);
            throw new InvalidMetadataException("collection " + context.getDBName() + "/" + context.getCollectionName() + " has invalid relationships definition", ex);
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
                logger.warn("document {}/{}/{} has an invalid relationship", context.getDBName(), context.getCollectionName(), context.getDocumentId(), ex);
            }
        }

        return links;
    }
}