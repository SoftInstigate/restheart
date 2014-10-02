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
import com.mongodb.MongoException;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.GetHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.IllegalQueryParamenterException;
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
     * Creates a new instance of GetCollectionHandler
     */
    public GetCollectionHandler()
    {
        super(null);
    }

    @Override
    protected String generateContent(HttpServerExchange exchange, RequestContext context, int page, int pagesize, Deque<String> sortBy, Deque<String> filterBy, Deque<String> filter)
    {
        DBCollection coll = CollectionDAO.getCollection(context.getDBName(), context.getCollectionName());

        // ***** get data
        List<Map<String, Object>> data = null;

        try
        {
            data = CollectionDAO.getCollectionData(coll, page, pagesize, sortBy, filter);
        }
        catch (JSONParseException jpe) // the filter expression is not a valid json string
        {
            logger.error("invalid filter expression {}", filter, jpe);
            ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_BAD_REQUEST, "wrong request, filter expression is invalid", jpe);
            return null;
        }
        catch (MongoException me)
        {
            if (me.getMessage().matches(".*Can't canonicalize query.*")) // error with the filter expression during query execution
            {
                logger.error("invalid filter expression {}", filter, me);
                ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_BAD_REQUEST, "wrong request, filter expression is invalid", me);
                return null;
            }
            else
            {
                throw me;
            }
        }

        if (exchange.isComplete()) // if an error occured getting data, the exchange is already closed
        {
            return null;
        }

        // ***** return NOT_FOUND from here if collection is not existing 
        // (this is to avoid to check existance via the slow CollectionDAO.checkCollectionExists)
        if (data.isEmpty() && (context.getCollectionMetadata() == null || context.getCollectionMetadata().isEmpty()))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return null;
        }
        
        Object _size = context.getCollectionMetadata().get("@size");
        
        if (exchange.getQueryParameters().containsKey("count"))
        {
            _size = CollectionDAO.getCollectionSize(coll, exchange.getQueryParameters().get("filter"));

            context.getCollectionMetadata().put("@size", _size);
        }
        
        long size = (_size == null ? -1 : Long.valueOf("" + _size)); 

        // ***** return hal document
        try
        {
            return generateCollectionContent(exchange, context.getCollectionMetadata(), data, page, pagesize, size, sortBy, filterBy, filter);
        }
        catch (IllegalQueryParamenterException ex)
        {
            ResponseHelper.endExchangeWithError(exchange, HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
            return null;
        }
    }
}