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
package com.softinstigate.restheart.handlers.account;

import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.utils.JSONHelper;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author uji
 */
public class GetAccountHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    final Charset charset = Charset.forName("utf-8");  

    /**
     * Creates a new instance of EntityResource
     */
    public GetAccountHandler()
    {
    }

    /**
     * @returns the list of db references.
     * @param exchange
     * @throws Exception 
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext c = new RequestContext(exchange);

        int skip = 0;
        int limit = 100;

        Deque<String> _skips = exchange.getQueryParameters().get("skip");
        Deque<String> _limits = exchange.getQueryParameters().get("limit");

        if (_skips != null && !_skips.isEmpty())
        {
            try
            {
                skip = Integer.parseInt(_skips.getFirst());
            }
            catch (NumberFormatException nfwe)
            {
                skip = 0;
            }
        }

        if (_limits != null && !_limits.isEmpty())
        {
            try
            {
                limit = Integer.parseInt(_limits.getFirst());
            }
            catch (NumberFormatException nfwe)
            {
                limit = 100;
            }
        }

        String content = getDBReferences(exchange.getRequestURL(), skip, limit);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.setResponseContentLength(content.length());
        exchange.setResponseCode(200);

        exchange.getResponseSender().send(content);
        
        exchange.endExchange();
    }

    /**
     * method for getting documents of collection coll
     *
     * @param skip
     * @param limit default is 100
     * @return
     */
    private String getDBReferences(String baseUrl, int skip, int limit)
    {
        List<String> dbs = client.getDatabaseNames();
        
        if (dbs == null)
            dbs = new ArrayList<>();
        
        dbs = dbs.subList(skip, limit+skip > dbs.size() ? dbs.size() : limit + skip );
        
        Map<String, Map> refs = new HashMap<>();
        
        for (String db: dbs)
        {
            refs.putAll(JSONHelper.getReference(baseUrl, db));
        }
        
        return JSON.serialize(refs);
    }
}