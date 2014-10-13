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
package com.softinstigate.restheart.utils;

import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 *
 * @author uji
 */
public class URLUtilis
{
    static public String removeTrailingSlashes(String s)
    {
        if (s == null || s.length() < 2)
        {
            return s;
        }

        if (s.trim().charAt(s.length() - 1) == '/')
        {
            return removeTrailingSlashes(s.substring(0, s.length() - 1));
        }
        else
        {
            return s.trim();
        }
    }

    static public String getPerentPath(String path)
    {
        if ((path == null) || path.equals("") || path.equals("/"))
        {
            return path;
        }

        int lastSlashPos = path.lastIndexOf('/');

        if (lastSlashPos > 0)
        {
            return path.substring(0, lastSlashPos); //strip off the slash
        }
        else if (lastSlashPos == 0)
        {
            return "/";
        }
        else
        {
            return ""; //we expect people to add  + "/somedir on their own
        }
    }

    static public String getPrefixUrl(HttpServerExchange exchange)
    {
        return exchange.getRequestURL().replaceAll(exchange.getRelativePath(), "");
    }

    /**
     *
     * @param exchange
     * @return the request path without the query string
     */
    static public String getRequestPath(HttpServerExchange exchange)
    {
        return exchange.getRequestPath().replaceAll(exchange.getQueryString(), "");
    }

    static public String getUriWithDocId(String dbName, String collName, String documentId)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("/").append(dbName).append("/").append(collName).append("/").append(documentId);

        return sb.toString().replaceAll(" ", "");
    }

    static public String getUriWithFilterMany(String dbName, String collName, String referenceField, String ids)
    {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter=<"ref":<"$in":<"a","b","c">>
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter=<").append("'").append(referenceField).append("'").append(":").append("<'$in'").append(":").append(ids).append(">>");

        return sb.toString().replaceAll(" ", "");
    }

    static public String getUriWithFilterOne(String dbName, String collName, String referenceField, String ids)
    {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter=<"ref":<"$in":<"a","b","c">>
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter=<").append("'").append(referenceField).append("'").append(":").append(ids).append(">");

        return sb.toString().replaceAll(" ", "");
    }

    static public String getUriWithFilterManyInverse(String dbName, String collName, String referenceField, String ids)
    {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter=<'referenceField':<"$elemMatch":<'ids'>>>
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter=<'").append(referenceField).append("':<").append("'$elemMatch':<'$eq':").append(ids).append(">>>>");

        return sb.toString().replaceAll(" ", "");
    }

    public static String getQueryStringRemovingParams(HttpServerExchange exchange, String... paramsToRemove)
    {
        String ret = exchange.getQueryString();

        if (ret == null || ret.isEmpty())
        {
            return ret;
        }

        if (paramsToRemove == null)
        {
            return ret;
        }

        for (String key : paramsToRemove)
        {
            Deque<String> values = exchange.getQueryParameters().get(key);

            if (values != null)
            {
                for (String value : values)
                {
                    ret = ret.replaceAll(key + "=" + value + "&", "");
                    ret = ret.replaceAll(key + "=" + value + "$", "");
                }
            }
        }

        return ret;
    }
}
