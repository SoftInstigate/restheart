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
package com.softinstigate.restheart.handlers.indexes;

import com.softinstigate.restheart.db.IndexDAO;
import com.softinstigate.restheart.handlers.GetHandler;
import com.softinstigate.restheart.utils.JSONHelper;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class GetIndexesHandler extends GetHandler
{
    private static final Logger logger = LoggerFactory.getLogger(GetIndexesHandler.class);

    /**
     * Creates a new instance of GetCollectionHandler
     */
    public GetIndexesHandler()
    {
        super(null);
    }

    @Override
    protected String generateContent(HttpServerExchange exchange, RequestContext context, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        List<Map<String, Object>> indexes = IndexDAO.getCollectionIndexes(context.getDBName(), context.getCollectionName());
        
        return JSONHelper.getCollectionHal(exchange.getRequestURL(), null, null, indexes).toString();
    }
}