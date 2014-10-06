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

import com.mongodb.DBObject;
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.json.hal.HALDocumentSender;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.net.URISyntaxException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class GetDBHandler extends PipedHttpHandler
{
    private static final Logger logger = LoggerFactory.getLogger(GetDBHandler.class);

    /**
     * Creates a new instance of GetDBHandler
     */
    public GetDBHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        List<String> colls = DBDAO.getDbCollections(DBDAO.getDB(context.getDBName()));
        
        List<DBObject> data;
        
        try
        {
            data = DBDAO.getData(context.getDBName(), colls, context.getPage(), context.getPagesize());
            
            exchange.setResponseCode(HttpStatus.SC_OK);
            HALDocumentSender.sendCollection(exchange, context, data, data.size());
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
