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
package com.softinstigate.restheart.handlers.root;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.json.hal.HALDocumentSender;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class GetRootHandler extends PipedHttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    private static final Logger logger = LoggerFactory.getLogger(GetRootHandler.class);

    /**
     * Creates a new instance of GetRootHandler
     */
    public GetRootHandler()
    {
        super(null);
    }


    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        List<String> _dbs = client.getDatabaseNames();

        // filter out reserved resources
        List<String> dbs = _dbs.stream().filter(db -> ! RequestContext.isReservedResourceDb(db)).collect(Collectors.toList());
        
        if (dbs == null)
        {
            dbs = new ArrayList<>();
        }

        int size = dbs.size();

        Collections.sort(dbs); // sort by id

        // apply page and pagesize
        dbs = dbs.subList((context.getPage() - 1) * context.getPagesize(), (context.getPage() - 1) * context.getPagesize() + context.getPagesize() > dbs.size() ? dbs.size() : (context.getPage() - 1) * context.getPagesize() + context.getPagesize());

        List<DBObject> data = new ArrayList<>();

        dbs.stream().map(
                (db) ->
                {
                    BasicDBObject properties = new BasicDBObject();

                    properties.put("_id", db);
                    return properties;
                }
        ).forEach((item) -> { data.add(item); });

        try
        {
            exchange.setResponseCode(HttpStatus.SC_OK);
            HALDocumentSender.sendCollection(exchange, context, data, size);
            exchange.endExchange();
        }
        catch (IllegalQueryParamenterException ex)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
        }
        catch (URISyntaxException ex)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}