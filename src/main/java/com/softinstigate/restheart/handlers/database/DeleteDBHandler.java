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
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.util.List;

/**
 *
 * @author uji
 */
public class DeleteDBHandler extends PipedHttpHandler
{
    /**
     * Creates a new instance of EntityResource
     */
    public DeleteDBHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        DB db = DBDAO.getDB(context.getDBName());
        
        List<String> _colls = DBDAO.getDbCollections(db);
        
        // count filtering out collection starting with @, e.g. @metadata collection
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