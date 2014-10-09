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

/**
 *
 * @author uji
 */
public class URLUtilis
{
    static public String removeTrailingSlashes(String s)
    {
        if (s == null || s.length() < 2)
            return s;
        
        if (s.trim().charAt(s.length() - 1) == '/')
        {
            return removeTrailingSlashes(s.substring(0, s.length() - 1));
        }
        else
        {
            return s.trim();
        }
    }
    
    static public String getPrefixUrl(HttpServerExchange exchange)
    {
        return exchange.getRequestURL().replaceAll(exchange.getRelativePath(), "");
    }
    
    static public String getUrlWithDocId(String baseUrl, String dbName, String collName, String documentId)
    {
        StringBuilder sb = new StringBuilder();
        
        sb.append(URLUtilis.removeTrailingSlashes(baseUrl)).append("/").append(dbName).append("/").append(collName).append("/").append(documentId);
                
        return sb.toString().replaceAll(" ", "");
    }
    
    static public String getUrlWithFilterMany(String baseUrl, String dbName, String collName, String referenceField, String ids)
    {
        StringBuilder sb = new StringBuilder();
        
        ///db/coll/?filter=<"ref":<"$in":<"a","b","c">>
        sb.append(URLUtilis.removeTrailingSlashes(baseUrl)).append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter=<").append("'").append(referenceField).append("'").append(":").append("<'$in'").append(":").append(ids).append(">>");
        
        return sb.toString().replaceAll(" ", "");
    }
    
    static public String getUrlWithFilterOne(String baseUrl, String dbName, String collName, String referenceField, String ids)
    {
        StringBuilder sb = new StringBuilder();
        
        ///db/coll/?filter=<"ref":<"$in":<"a","b","c">>
        sb.append(URLUtilis.removeTrailingSlashes(baseUrl)).append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter=<").append("'").append(referenceField).append("'").append(":").append(ids).append(">");
        
        return sb.toString().replaceAll(" ", "");
    }
    
    static public String getUrlWithFilterManyInverse(String baseUrl, String dbName, String collName, String referenceField, String ids)
    {
        StringBuilder sb = new StringBuilder();
        
        ///db/coll/?filter=<'referenceField':<"$elemMatch":<'ids'>>>
        sb.append(URLUtilis.removeTrailingSlashes(baseUrl)).append("/").append(dbName).append("/").append(collName).append("?")
                .append("filter=<'").append(referenceField).append("':<").append("'$elemMatch':<'$eq':").append(ids).append(">>>>");
        
        return sb.toString().replaceAll(" ", "");
    }
}
