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
package com.softinstigate.restheart.json.hal;

import com.mongodb.DBObject;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.json.metadata.InvalidMetadataException;
import com.softinstigate.restheart.json.metadata.Relationship;
import com.softinstigate.restheart.utils.URLUtilis;
import com.theoryinpractise.halbuilder.api.Representation;
import com.theoryinpractise.halbuilder.api.RepresentationFactory;
import com.theoryinpractise.halbuilder.json.JsonRepresentationFactory;
import com.theoryinpractise.halbuilder.json.JsonRepresentationWriter;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import org.bson.types.ObjectId;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author uji
 */
public class HALDocumentSender
{
    public static final String JSON_MEDIA_TYPE = "application/json";
    public static final String HAL_JSON_MEDIA_TYPE = "application/hal+json";

    private static final Logger logger = LoggerFactory.getLogger(HALDocumentSender.class);

    private static final RepresentationFactory representationFactory = new JsonRepresentationFactory();

    private static final JsonRepresentationWriter jrw = new JsonRepresentationWriter();

    private static final HashSet<URI> flags = new HashSet<>();

    static
    {
        flags.add(RepresentationFactory.PRETTY_PRINT);
        flags.add(RepresentationFactory.STRIP_NULLS);
    }

    static public void sendCollection(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException, URISyntaxException
    {
        String baseUrl = exchange.getRequestURL();
        Representation rep = representationFactory.newRepresentation(baseUrl);

        DBObject collProps = context.getCollectionProps();

        if (collProps != null) // this is a collection, add the collection properties
        {
            addData(rep, collProps);
        }
        else // this is a db, add the db properties
        {
            DBObject dbProps = context.getDbProps();
            
            if (dbProps != null)
            {
                addData(rep, dbProps);
            }
        }
        
        if (size > 0)
        {
            float _size = size + 0f;
            float _pagesize = context.getPagesize() + 0f;

            rep.withProperty("@size", size);
            rep.withProperty("@total_pages", Math.max(1, Math.round(Math.nextUp(_size / _pagesize))));
        }

        if (embeddedData != null)
        {
            long count = embeddedData.stream().filter((props) -> props.keySet().stream().anyMatch((k) -> k.equals("id") || k.equals("_id"))).count();

            rep.withProperty("@returned", "" + count);

            if (!embeddedData.isEmpty()) // embedded documents
            {
                for (DBObject d : embeddedData)
                {
                    Object _id = d.get("_id");

                    if (_id != null && (_id instanceof String || _id instanceof ObjectId))
                    {
                        Representation nrep = representationFactory.newRepresentation(getReferenceLink(baseUrl, _id.toString()));
                        
                        addData(nrep, d);
                        
                        // document links
                        TreeMap<String, String> links = null;
                        
                        try
                        {
                            links = getRelationshipsLinks(exchange, context, d);
                        }
                        catch (IllegalArgumentException | IllegalQueryParamenterException | URISyntaxException ex)
                        {
                            logger.warn("document {}/{}/{} has a wrong relationship", context.getDBName(), context.getCollectionName(), context.getDocumentId(), ex);
                        }

                        if (links != null)
                        {
                            for (String k : links.keySet())
                            {
                                nrep = nrep.withLink(k, links.get(k));
                            }
                        }
                        
                        rep = rep.withRepresentation("rh:embedded", nrep);
                    }
                    else
                    {
                        logger.error("document missing string _id field", d);
                    }
                }
            }
        }

        // collection links
        TreeMap<String, String> links;

        links = getPaginationLinks(exchange, context, size);

        if (links != null)
        {
            for (String k : links.keySet())
            {
                rep = rep.withLink(k, links.get(k));
            }
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        jrw.write(rep, flags, new OutputStreamWriter(exchange.getOutputStream()));
    }

    public static void sendDocument(HttpServerExchange exchange, RequestContext context, DBObject data)
            throws IllegalQueryParamenterException, URISyntaxException
    {
        String baseUrl = exchange.getRequestURL();

        Representation rep = representationFactory.newRepresentation(baseUrl);

        // document properties
        for (String key : data.keySet())
        {
            Object value = data.get(key);

            if (value instanceof ObjectId)
            {
                value = value.toString();
            }

            rep = rep.withProperty(key, value);
        }
        // document links
        TreeMap<String, String> links = null;

        try
        {
            links = getRelationshipsLinks(exchange, context, data);
        }
        catch (IllegalArgumentException | IllegalQueryParamenterException | URISyntaxException ex)
        {
            logger.warn("document {}/{}/{} has a wrong relationship", context.getDBName(), context.getCollectionName(), context.getDocumentId(), ex);
        }

        if (links != null)
        {
            for (String k : links.keySet())
            {
                rep = rep.withLink(k, links.get(k));
            }
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        jrw.write(rep, flags, new OutputStreamWriter(exchange.getOutputStream()));
    }

    private static void addData(Representation rep, DBObject data)
    {
        // collection properties
        for (String key : data.keySet())
        {
            Object value = data.get(key);

            if (value instanceof ObjectId)
            {
                value = value.toString();
            }

            rep = rep.withProperty(key, value);
        }
    }

    private static TreeMap<String, String> getPaginationLinks(HttpServerExchange exchange, RequestContext context, long size) throws IllegalQueryParamenterException, URISyntaxException
    {
        String baseUrl = exchange.getRequestURL();
        String queryString = exchange.getQueryString();

        int page = context.getPage();
        int pagesize = context.getPagesize();
        int totalPages = 0;

        if (size > 0)
        {
            float _size = size + 0f;
            float _pagesize = pagesize + 0f;

            totalPages = Math.max(1, Math.round(Math.nextUp(_size / _pagesize)));
        }

        TreeMap<String, String> links = new TreeMap<>();

        if (queryString == null || queryString.isEmpty())
        {
            links.put("self", baseUrl);
            links.put("next", baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize);
        }
        else
        {
            String queryString2 = removePagingParamsFromQueryString(queryString);

            links.put("self", baseUrl + "?" + queryString2);

            if (queryString2 == null || queryString2.isEmpty())
            {
                links.put("first", baseUrl + "?pagesize=" + pagesize);
                links.put("next", baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize);

                if (totalPages > 0) // i.e. the url contains the count paramenter
                {
                    if (page < totalPages)
                    {
                        links.put("last", baseUrl + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize);
                        links.put("next", baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2);
                    }
                    else
                    {
                        links.put("last", baseUrl + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize);
                    }
                }

                if (page > 1)
                {
                    links.put("previous", baseUrl + (page >= 2 ? "?page=" + (page - 1) : "") + (page > 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize));
                }
            }
            else
            {
                links.put("first", baseUrl + "?pagesize=" + pagesize + "&" + queryString2);

                if (totalPages <= 0)
                {
                    links.put("next", baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2);
                }

                if (totalPages > 0) // i.e. the url contains the count paramenter
                {
                    if (page < totalPages)
                    {
                        links.put("last", baseUrl + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryString2);
                        links.put("next", baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2);
                    }
                    else
                    {
                        links.put("last", baseUrl + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryString2);
                    }
                }

                if (page > 1)
                {
                    links.put("previous", baseUrl + (page >= 2 ? "?page=" + (page - 1) : "") + (page > 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize) + "&" + queryString2);
                }
            }
        }

        return links;
    }

    private static TreeMap<String, String> getRelationshipsLinks(HttpServerExchange exchange, RequestContext context, DBObject data) throws IllegalArgumentException, IllegalQueryParamenterException, URISyntaxException
    {
        String prefixUrl = URLUtilis.getPrefixUrl(exchange);

        TreeMap<String, String> links = new TreeMap<>();

        List<Relationship> rels;

        try
        {
            rels = Relationship.getFromJson((DBObject) context.getCollectionProps());
        }
        catch (InvalidMetadataException ex)
        {
            logger.error("collection {}/{} invalid relationships definition", context.getDBName(), context.getCollectionName(), ex);
            throw new IllegalQueryParamenterException("collection {}/{} invalid relationships definition", ex);
        }

        if (rels == null)
            return links;
        
        for (Relationship rel : rels)
        {
            String link = rel.getRelationshipLink(prefixUrl, context.getDBName(), context.getCollectionName(), data);

            if (link != null)
            {
                links.put(rel.getRel(), link);
            }
        }

        return links;
    }

    private static String removePagingParamsFromQueryString(String queryString)
    {
        if (queryString == null)
        {
            return null;
        }

        String ret = queryString;
        ret = ret.replaceAll("page=.?$", "");
        ret = ret.replaceAll("pagesize=.?$", "");

        ret = ret.replaceAll("page=.?&", "");
        ret = ret.replaceAll("pagesize=.?&", "");

        return ret;
    }

    static private String getReferenceLink(String parentUrl, String referencedName)
    {
        return URLUtilis.removeTrailingSlashes(parentUrl) + "/" + referencedName;
    }
}
