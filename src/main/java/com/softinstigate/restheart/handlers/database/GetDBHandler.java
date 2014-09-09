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
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import java.util.List;
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
     * Creates a new instance of EntityResource
     */
    public GetDBHandler()
    {
    }

    @Override
    protected String generateContent(HttpServerExchange exchange, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        RequestContext rc = new RequestContext(exchange);

        List<String> colls = DBDAO.getDbCollections(DBDAO.getDB(rc.getDBName()));
        
        return generateCollectionContent(exchange.getRequestURL(), exchange.getQueryString(), DBDAO.getDbMetaData(rc.getDBName(), colls), DBDAO.getData(colls, page, pagesize, sortBy, filterBy, filter), page, pagesize, DBDAO.getDBSize(colls), sortBy, filterBy, filter);
    }
}
