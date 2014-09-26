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
package com.softinstigate.restheart.handlers.collection;

import com.mongodb.DBCollection;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.GetHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import com.softinstigate.restheart.utils.ResponseHelper;
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
public class GetCollectionHandler extends GetHandler
{
    private static final Logger logger = LoggerFactory.getLogger(GetCollectionHandler.class);

    /**
     * Creates a new instance of EntityResource
     */
    public GetCollectionHandler()
    {
        super(null);
    }

    @Override
    protected String generateContent(HttpServerExchange exchange, RequestContext context, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        DBCollection coll = CollectionDAO.getCollection(context.getDBName(), context.getCollectionName());

        Map<String, Object> metadata = CollectionDAO.getCollectionMetadata(coll);
        
        Object etag = metadata.get("@etag");
        
        // in case the request contains the IF_NONE_MATCH header with the current etag value, just return 304 NOT_MODIFIED code
        if (etag != null && checkEtagHeader(exchange, etag.toString()))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_MODIFIED);
            return null;
        }
        
        List<Map<String, Object>> data = CollectionDAO.getCollectionData(coll, page, pagesize, sortBy, filterBy, filter);
        
        long size = CollectionDAO.getCollectionSize(coll);

        return generateCollectionContent(exchange, metadata, data, page, pagesize, size, sortBy, filterBy, filter);
    }
}
