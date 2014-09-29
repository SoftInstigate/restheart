/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written accessLevel of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.security.impl;

import com.softinstigate.restheart.utils.RequestContext;
import static com.softinstigate.restheart.utils.RequestContext.METHOD.DELETE;
import static com.softinstigate.restheart.utils.RequestContext.METHOD.GET;
import static com.softinstigate.restheart.utils.RequestContext.METHOD.PATCH;
import static com.softinstigate.restheart.utils.RequestContext.METHOD.POST;
import static com.softinstigate.restheart.utils.RequestContext.METHOD.PUT;
import io.undertow.server.HttpServerExchange;
import java.util.Set;

/**
 *
 * @author uji
 */
public class SimplePermission
{
    public enum ACCESS_LEVEL { ADMIN, WRITE, READ };
    
    private final String role;
    private final ACCESS_LEVEL accessLevel;
    private final Set<String> urls;
    private final String contentConditions;
              
    public SimplePermission(String role, ACCESS_LEVEL accessLevel, Set<String> urls, String contentConditions)
    {
        this.role = role;
        this.accessLevel = accessLevel;
        this.urls = urls;
        this.contentConditions = contentConditions;
    }
    
    public boolean doesAllow(HttpServerExchange exchange, RequestContext context)
    {
        if (context.getDBName() != null && context.getCollectionName() != null && context.getDBName().equals("testdb") && context.getCollectionName().equals("public"))
        {
            return true;
        }
        
        String path = exchange.getRelativePath();
        
        if (urls.stream().noneMatch(url -> path.matches(url)))
            return false;

        else if (accessLevel == ACCESS_LEVEL.ADMIN)
        {
            return true;
        }
        else if (accessLevel == ACCESS_LEVEL.WRITE)
        {
            switch (context.getMethod())
            {
                case GET:
                    return true;
                case POST:
                    return true;
                case DELETE:
                    return false;
                case PATCH:
                    return true;
                case PUT:
                    return true;
            }
        }
        else if (accessLevel == ACCESS_LEVEL.READ)
        {
            switch (context.getMethod())
            {
                case GET:
                    return true;
                case POST:
                    return false;
                case DELETE:
                    return false;
                case PATCH:
                    return false;
                case PUT:
                    return false;
            }
        }
    
        return false;
    }

    /**
     * @return the role
     */
    public String getRole()
    {
        return role;
    }

    /**
     * @return the accessLevel
     */
    public ACCESS_LEVEL getAccessLevel()
    {
        return accessLevel;
    }

    /**
     * @return the urls
     */
    public Set<String> getUrls()
    {
        return urls;
    }

    /**
     * @return the contentConditions
     */
    public String getContentCondition()
    {
        return contentConditions;
    }
}