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

package com.softinstigate.restart.utils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author uji
 */
public class RequestContext
{
    public enum TYPE { ERROR, DB, COLLECTION, DOCUMENT };
    public enum METHOD { GET, POST, PUT, DELETE, OTHER };
    
    private TYPE type;
    private METHOD method;
    private final String[] pathTokens;
    
    public RequestContext(HttpServerExchange exchange)
    {
        String path = exchange.getRequestPath();
        pathTokens = path.split("/"); // "/db/collection/document" --> { "", "db", "collection", "document" }
        
        if (pathTokens.length < 2)
        {
            type = TYPE.ERROR;
        } else if (pathTokens.length < 3)
        {
            type = TYPE.DB;
        } else if (pathTokens.length < 4)
        {
            type = TYPE.COLLECTION;
        } else
        {
            type = TYPE.DOCUMENT;
        }
        
        HttpString _method = exchange.getRequestMethod();
        
        if (Methods.GET.equals(_method))
            this.method = METHOD.GET;
        else if (Methods.POST.equals(_method))
            this.method = METHOD.POST;
        else if (Methods.PUT.equals(_method))
            this.method = METHOD.PUT;
        else if (Methods.DELETE.equals(_method))
            this.method = METHOD.DELETE;
        else
            this.method = METHOD.OTHER;
    }
    
    public TYPE getType()
    {
        return type;
    }
    
    public String getDBName()
    {
        if (pathTokens.length > 0)
            return pathTokens[1];
        else
            return null;
    }
    
    public String getCollectionName()
    {
        if (pathTokens.length > 1)
            return pathTokens[2];
        else
            return null;
    }
    
    public String getDocumentId()
    {
        if (pathTokens.length > 0)
            return pathTokens[2];
        else
            return null;
    }
    
    public URI getUri()
    {
        try
        {
            return new URI(Arrays.asList(pathTokens).stream().reduce("/", (t1,t2) -> t1 + "/" + t2));
        }
        catch (URISyntaxException ex)
        {
            Logger.getLogger(RequestContext.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public METHOD getMethod()
    {
        return method;
    }
}
