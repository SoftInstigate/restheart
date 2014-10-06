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
package com.softinstigate.restheart.handlers;

import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;

/**
 *
 * @author uji
 */
public class RequestContextInjecterHandler extends PipedHttpHandler
{
    private final String prefixUrl;
    private final String db;
    
    public RequestContextInjecterHandler(String prefixUrl, String db, PipedHttpHandler next)
    {
        super(next);
        
        if (!prefixUrl.startsWith("/"))
            throw new IllegalArgumentException("prefix url must start with /");
        
        this.prefixUrl = removeTrailingSlashes(prefixUrl);
        this.db = db;
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        RequestContext rcontext = new RequestContext(exchange, prefixUrl, db);
        
        Deque<String> __pagesize = exchange.getQueryParameters().get("pagesize");

        int page = 1; // default page
        int pagesize = 100; // default pagesize
        
        if (__pagesize != null && !(__pagesize.isEmpty()))
        {
            try
            {
                pagesize = Integer.parseInt(__pagesize.getFirst());
            }
            catch (NumberFormatException ex)
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal pagesize paramenter, it is not a number", ex);
                return;
            }
        }
        
        if (pagesize < 1 || pagesize > 1000)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal page parameter, pagesize must be >= 0 and <= 1000");
            return;
        } else
        {
            rcontext.setPagesize(pagesize);
        }

        Deque<String> __page = exchange.getQueryParameters().get("page");

        if (__page != null && !(__page.isEmpty()))
        {
            try
            {
                page = Integer.parseInt(__page.getFirst());
            }
            catch (NumberFormatException ex)
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal page paramenter, it is not a number", ex);
                return;
            }
        }
        
        if (page < 1)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal page paramenter, it is < 1");
            return;
        }
        else
        {
            rcontext.setPage(page);
        }
        
        Deque<String> __count = exchange.getQueryParameters().get("count");
        
        if (__count != null)
        {
            rcontext.setCount(true);
        }
        
        rcontext.setSortBy(exchange.getQueryParameters().get("sort_by"));
        rcontext.setFilter(exchange.getQueryParameters().get("filter"));
        
        next.handleRequest(exchange, rcontext);
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        next.handleRequest(exchange, new RequestContext(exchange, prefixUrl, db));
    }
    
    private String removeTrailingSlashes(String s)
    {
        if (s.endsWith("/") && s.length() > 1)
            return removeTrailingSlashes(s.substring(0, s.length()-1));
        else
            return s;
    }
}