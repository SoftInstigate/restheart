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
package com.softinstigate.restheart.handlers.database;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author uji
 */
public class DeleteDBHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    /**
     * Creates a new instance of EntityResource
     */
    public DeleteDBHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange)
    {
        RequestContext rc = new RequestContext(exchange);

        DB db = client.getDB(rc.getDBName());

        List<String> _colls = new ArrayList(db.getCollectionNames());

        // filter out collection starting with @, e.g. @metadata collection
        long no = _colls.stream().filter(coll -> (!coll.startsWith("@") && !coll.startsWith("system."))).count();
        
        if (no > 0)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_ACCEPTABLE);
        }
        else
        {
            db.dropDatabase();
            
            ResponseHelper.endExchange(exchange, HttpStatus.SC_GONE);
        }
    }
}