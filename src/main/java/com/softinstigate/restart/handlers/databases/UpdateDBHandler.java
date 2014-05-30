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
package com.softinstigate.restart.handlers.databases;

import com.mongodb.MongoClient;
import com.softinstigate.restart.db.MongoDBClientSingleton;
import com.softinstigate.restart.utils.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.nio.charset.Charset;

/**
 *
 * @author uji
 */
public class UpdateDBHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    final Charset charset = Charset.forName("utf-8");  

    /**
     * Creates a new instance of EntityResource
     */
    public UpdateDBHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext c = new RequestContext(exchange);
        
        throw new RuntimeException("not yet implemented");
    }
}