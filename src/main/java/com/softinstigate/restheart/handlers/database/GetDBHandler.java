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

import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.handlers.GetHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class GetDBHandler extends GetHandler
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
    protected String generateContent(HttpServerExchange exchange, RequestContext context, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        List<String> colls = DBDAO.getDbCollections(DBDAO.getDB(context.getDBName()));
        
        List<Map<String, Object>> data;
        try
        {
            data = DBDAO.getData(context.getDBName(), colls, page, pagesize, sortBy, filterBy, filter);
            return generateCollectionContent(exchange, context.getDbMetadata(), data, page, pagesize, DBDAO.getDBSize(colls), sortBy, filterBy, filter);
        }
        catch (IllegalQueryParamenterException ex)
        {
            ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
            return null;
        }
    }
}
