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
package com.softinstigate.restheart.handlers.collection;

import com.softinstigate.restheart.hal.*;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import static com.softinstigate.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.handlers.document.DocumentRepresentationFactory;
import com.softinstigate.restheart.utils.URLUtilis;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.util.Deque;
import java.util.List;
import java.util.TreeMap;
import org.bson.types.ObjectId;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author uji
 */
public class CollectionRepresentationFactory
{
    private static final Logger logger = LoggerFactory.getLogger(CollectionRepresentationFactory.class);

    static public void sendCollection(HttpServerExchange exchange, RequestContext context, List<DBObject> embeddedData, long size)
            throws IllegalQueryParamenterException
    {
        String requestPath = URLUtilis.removeTrailingSlashes(URLUtilis.getRequestPath(exchange));

        Representation rep = new Representation(requestPath);

        if (context.getType() == RequestContext.TYPE.COLLECTION) // this is a collection, add the collection properties (not true for collection_indexes
        {
            DBObject collProps = context.getCollectionProps();

            if (collProps != null) 
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
        }

        if (size > 0)
        {
            float _size = size + 0f;
            float _pagesize = context.getPagesize() + 0f;

            rep.addProperty("@size", size);
            rep.addProperty("@total_pages", Math.max(1, Math.round(Math.nextUp(_size / _pagesize))));
        }

        if (embeddedData != null)
        {
            long count = embeddedData.stream().filter((props) -> props.keySet().stream().anyMatch((k) -> k.equals("id") || k.equals("_id"))).count();

            rep.addProperty("@returned", "" + count);

            if (!embeddedData.isEmpty()) // embedded documents
            {
                for (DBObject d : embeddedData)
                {
                    Object _id = d.get("_id");

                    if (_id != null && (_id instanceof String || _id instanceof ObjectId))
                    {
                        Representation nrep = DocumentRepresentationFactory.getDocument(requestPath + "/" + _id.toString(), exchange, context, d);

                        rep.addRepresentation("rh:documents", nrep);
                    }
                    else
                    {
                        logger.error("document missing string _id field", d);
                    }
                }
            }
        }

        if (context.getType() != RequestContext.TYPE.COLLECTION_INDEXES)
        {
            // collection links
            TreeMap<String, String> links;

            links = getPaginationLinks(exchange, context, size);

            if (links != null)
            {
                for (String k : links.keySet())
                {
                    rep.addLink(new Link(k, links.get(k)));
                }
            }
        }

        if (context.getType() == RequestContext.TYPE.COLLECTION)
        {
            rep.addLink(new Link("rh:indexes", URLUtilis.removeTrailingSlashes(URLUtilis.getRequestPath(exchange)) + "/@indexes"));
        }
        
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
        exchange.getResponseSender().send(rep.toString());
    }

    private static void addData(Representation rep, DBObject data)
    {
        // collection properties
        data.keySet().stream().forEach((key) ->
        {
            Object value = data.get(key);

            if (value instanceof ObjectId)
            {
                rep.addProperty(key, value.toString());
            }
            else
            {
                rep.addProperty(key, value);
            }
        });
    }

    private static TreeMap<String, String> getPaginationLinks(HttpServerExchange exchange, RequestContext context, long size) throws IllegalQueryParamenterException
    {
        String requestPath = URLUtilis.getRequestPath(exchange);
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
            links.put("self", requestPath);
            links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize);
        }
        else
        {
            String queryString2 = removePagingParamsFromQueryString(queryString, exchange.getQueryParameters().get("page"), exchange.getQueryParameters().get("pagesize"));

            links.put("self", requestPath + "?" + queryString2);

            if (queryString2 == null || queryString2.isEmpty())
            {
                links.put("first", requestPath + "?pagesize=" + pagesize);
                links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize);

                if (totalPages > 0) // i.e. the url contains the count paramenter
                {
                    if (page < totalPages)
                    {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize);
                        links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2);
                    }
                    else
                    {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize);
                    }
                }

                if (page > 1)
                {
                    links.put("previous", requestPath + (page >= 2 ? "?page=" + (page - 1) : "") + (page > 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize));
                }
            }
            else
            {
                links.put("first", requestPath + "?pagesize=" + pagesize + "&" + queryString2);

                if (totalPages <= 0)
                {
                    links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2);
                }

                if (totalPages > 0) // i.e. the url contains the count paramenter
                {
                    if (page < totalPages)
                    {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryString2);
                        links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryString2);
                    }
                    else
                    {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryString2);
                    }
                }

                if (page > 1)
                {
                    links.put("previous", requestPath + (page >= 2 ? "?page=" + (page - 1) : "") + (page >= 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize) + "&" + queryString2);
                }
            }
        }

        return links;
    }

    private static String removePagingParamsFromQueryString(String queryString, Deque<String> page, Deque<String> pagesize)
    {
        if (queryString == null)
        {
            return null;
        }

        String ret = queryString;

        if (page != null)
        {
            for (String v : page)
            {
                ret = ret.replaceAll("page=" + v + "&", "");
                ret = ret.replaceAll("page=" + v + "$", "");
            }
        }

        if (pagesize != null)
        {
            for (String v : pagesize)
            {
                ret = ret.replaceAll("pagesize=" + v + "$", "");
                ret = ret.replaceAll("pagesize=" + v + "$", "");
            }
        }

        return ret;
    }
}
