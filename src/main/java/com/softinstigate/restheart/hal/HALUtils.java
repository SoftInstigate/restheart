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
package com.softinstigate.restheart.hal;

import com.mongodb.DBObject;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.URLUtilis;
import io.undertow.server.HttpServerExchange;
import java.util.TreeMap;
import org.bson.types.ObjectId;

/**
 *
 * @author uji
 */
public class HALUtils
{
    public static void addData(Representation rep, DBObject data)
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

    public static TreeMap<String, String> getPaginationLinks(HttpServerExchange exchange, RequestContext context, long size) throws IllegalQueryParamenterException
    {
        String requestPath = URLUtilis.removeTrailingSlashes(exchange.getRequestPath());
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
            links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize);
        }
        else
        {
            String queryStringNoPagingProps = URLUtilis.getQueryStringRemovingParams(exchange, "page", "pagesize");

            if (queryStringNoPagingProps == null || queryStringNoPagingProps.isEmpty())
            {
                links.put("first", requestPath + "?pagesize=" + pagesize);
                links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize);

                if (totalPages > 0) // i.e. the url contains the count paramenter
                {
                    if (page < totalPages)
                    {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize);
                        links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
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
                links.put("first", requestPath + "?pagesize=" + pagesize + "&" + queryStringNoPagingProps);

                if (totalPages <= 0)
                {
                    links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                }

                if (totalPages > 0) // i.e. the url contains the count paramenter
                {
                    if (page < totalPages)
                    {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                        links.put("next", requestPath + "?page=" + (page + 1) + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                    }
                    else
                    {
                        links.put("last", requestPath + (totalPages != 1 ? "?page=" + totalPages : "") + "&pagesize=" + pagesize + "&" + queryStringNoPagingProps);
                    }
                }

                if (page > 1)
                {
                    links.put("previous", requestPath + (page >= 2 ? "?page=" + (page - 1) : "") + (page >= 2 ? "&pagesize=" + pagesize : "?pagesize=" + pagesize) + "&" + queryStringNoPagingProps);
                }
            }
        }

        return links;
    }
}
