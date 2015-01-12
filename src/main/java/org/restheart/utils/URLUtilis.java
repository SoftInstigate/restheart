/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package org.restheart.utils;

import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Deque;

/**
 *
 * @author Andrea Di Cesare
 */
public class URLUtilis {

    /**
     * given string /ciao/this/has/trailings///// returns /ciao/this/has/trailings
     * 
     * @param s
     * @return the string s without the trailing slashes
     */
    static public String removeTrailingSlashes(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }

        if (s.trim().charAt(s.length() - 1) == '/') {
            return removeTrailingSlashes(s.substring(0, s.length() - 1));
        } else {
            return s.trim();
        }
    }
    
    /**
     * decode the percent encoded query string
     * 
     * @param qs
     * @return the undecoded string
     */
    static public String decodeQueryString(String qs) {
        try {
            return URLDecoder.decode(qs.replace("+", "%2B"), "UTF-8").replace("%2B", "+");
        } catch (UnsupportedEncodingException ex) {
            return null;
        }
    }

    /**
     *
     * @param path
     * @return
     */
    static public String getPerentPath(String path) {
        if (path == null || path.equals("") || path.equals("/")) {
            return path;
        }

        int lastSlashPos = path.lastIndexOf('/');

        if (lastSlashPos > 0) {
            return path.substring(0, lastSlashPos); //strip off the slash
        } else if (lastSlashPos == 0) {
            return "/";
        } else {
            return ""; //we expect people to add  + "/somedir on their own
        }
    }

    /**
     *
     * @param exchange
     * @return
     */
    static public String getPrefixUrl(HttpServerExchange exchange) {
        return exchange.getRequestURL().replaceAll(exchange.getRelativePath(), "");
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param documentId
     * @return
     */
    static public String getUriWithDocId(RequestContext context, String dbName, String collName, String documentId) {
        StringBuilder sb = new StringBuilder();

        sb.append("/").append(dbName).append("/").append(collName).append("/").append(documentId);

        return context.mapUri(sb.toString().replaceAll(" ", ""));
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param referenceField
     * @param ids
     * @return
     */
    static public String getUriWithFilterMany(RequestContext context, String dbName, String collName, String referenceField, String ids) {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={"ref":{"$in":{"a","b","c"}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={").append("'").append(referenceField).append("'").append(":")
                .append("{'$in'").append(":").append(ids).append("}}");

        return context.mapUri(sb.toString().replaceAll(" ", ""));
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param referenceField
     * @param ids
     * @return
     */
    static public String getUriWithFilterOne(RequestContext context, String dbName, String collName, String referenceField, String ids) {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={"ref":{"$in":{"a","b","c"}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={").append("'").append(referenceField).append("'")
                .append(":").append(ids).append("}");

        return context.mapUri(sb.toString().replaceAll(" ", ""));
    }

    /**
     *
     * @param context
     * @param dbName
     * @param collName
     * @param referenceField
     * @param ids
     * @return
     */
    static public String getUriWithFilterManyInverse(RequestContext context, String dbName, String collName, String referenceField, String ids) {
        StringBuilder sb = new StringBuilder();

        ///db/coll/?filter={'referenceField':{"$elemMatch":{'ids'}}}
        sb.append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter={'").append(referenceField)
                .append("':{").append("'$elemMatch':{'$eq':").append(ids).append("}}}");

        return context.mapUri(sb.toString().replaceAll(" ", ""));
    }

    /**
     *
     * @param exchange
     * @param paramsToRemove
     * @return
     */
    public static String getQueryStringRemovingParams(HttpServerExchange exchange, String... paramsToRemove) {
        String ret = exchange.getQueryString();

        if (ret == null || ret.isEmpty()) {
            return ret;
        }

        if (paramsToRemove == null) {
            return ret;
        }

        for (String key : paramsToRemove) {
            Deque<String> values = exchange.getQueryParameters().get(key);

            if (values != null) {
                for (String value : values) {
                    ret = ret.replaceAll(key + "=" + value + "&", "");
                    ret = ret.replaceAll(key + "=" + value + "$", "");
                }
            }
        }

        return ret;
    }
}
