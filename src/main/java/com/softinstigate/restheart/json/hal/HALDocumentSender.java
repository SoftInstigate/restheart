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
import com.theoryinpractise.halbuilder.api.Representation;
import com.theoryinpractise.halbuilder.api.RepresentationFactory;
import com.theoryinpractise.halbuilder.json.JsonRepresentationFactory;
import com.theoryinpractise.halbuilder.json.JsonRepresentationWriter;
import io.undertow.server.HttpServerExchange;
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

        if (collProps != null) // collection properties
        {
            addData(rep, collProps);

            if (size > 0)
            {
                float _size = size + 0f;
                float _pagesize = context.getPagesize() + 0f;

                rep.withProperty("@total_pages", Math.max(1, Math.round(Math.nextUp(_size / _pagesize))));
            }
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
        TreeMap<String, URI> links;

        links = getPaginationLinks(exchange, context, size);

        if (links != null)
        {
            for (String k : links.keySet())
            {
                rep = rep.withLink(k, links.get(k));
            }
        }

        // document links
        jrw.write(rep, flags, new OutputStreamWriter(exchange.getOutputStream()));
    }

    public static void sendDocument(HttpServerExchange exchange, RequestContext context, DBObject data)
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

    private static TreeMap<String, URI> getPaginationLinks(HttpServerExchange exchange, RequestContext context, long size) throws IllegalQueryParamenterException, URISyntaxException
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

        TreeMap<String, URI> links = new TreeMap<>();

        if (queryString == null || queryString.isEmpty())
        {
            links.put("self", new URI(baseUrl));
            links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize));
        }
        else
        {
            String queryString2 = removePagingParamsFromQueryString(queryString);

            links.put("self", new URI(baseUrl + "?" + queryString2));

            if (queryString2 == null || queryString2.isEmpty())
            {
                links.put("first", new URI(baseUrl + "?pagesize=" + pagesize));
                links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize));

                if (totalPages > 0) // i.e. the url contains the count paramenter
                {
                    if (page < totalPages)
                    {
                        links.put("last", new URI(baseUrl + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize));
                        links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2));
                    }
                    else
                    {
                        links.put("last", new URI(baseUrl + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize));
                    }
                }

                if (page > 1)
                {
                    links.put("previous", new URI(baseUrl + (page >= 2 ? "?page=" + (page - 1) : "") + (page > 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize)));
                }
            }
            else
            {
                links.put("first", new URI(baseUrl + "?pagesize=" + pagesize + "&" + queryString2));

                if (totalPages <= 0)
                {
                    links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2));
                }

                if (totalPages > 0) // i.e. the url contains the count paramenter
                {
                    if (page < totalPages)
                    {
                        links.put("last", new URI(baseUrl + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryString2));
                        links.put("next", new URI(baseUrl + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2));
                    }
                    else
                    {
                        links.put("last", new URI(baseUrl + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryString2));
                    }
                }

                if (page > 1)
                {
                    links.put("previous", new URI(baseUrl + (page >= 2 ? "?page=" + (page - 1) : "") + (page > 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize) + "&" + queryString2));
                }
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

    static private URI getReferenceLink(String parentUrl, String referencedName)
    {
        try
        {
            return new URI(removeTrailingSlashes(parentUrl) + "/" + referencedName);
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating URI from {} + / + {}", parentUrl, referencedName, ex);
        }

        return null;
    }

    static private String removeTrailingSlashes(String s)
    {
        if (s.trim().charAt(s.length() - 1) == '/')
        {
            return removeTrailingSlashes(s.substring(0, s.length() - 1));
        }
        else
        {
            return s.trim();
        }
    }
}
