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

import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author uji
 */
public class UrlToDbMapperHandler extends PipedHttpHandler
{
    private final String prefixUrl;
    private final String db;

    public UrlToDbMapperHandler(String prefixUrl, String db, PipedHttpHandler next)
    {
        super(next);
        this.prefixUrl = removeTrailingSlashes(prefixUrl);
        this.db = db;
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        next.handleRequest(exchange, new RequestContext(exchange, prefixUrl, db));
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