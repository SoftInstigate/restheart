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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.hal.metadata.InvalidMetadataException;
import com.softinstigate.restheart.hal.metadata.Relationship;
import com.softinstigate.restheart.handlers.injectors.LocalCachesSingleton;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.handlers.document.DocumentRepresentationFactory;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class PutCollectionHandler extends PipedHttpHandler
{
    private static final Logger logger = LoggerFactory.getLogger(PutCollectionHandler.class);

    /**
     * Creates a new instance of PutCollectionHandler
     */
    public PutCollectionHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (context.getCollectionName().isEmpty() || context.getCollectionName().startsWith("_"))
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "wrong request, collection name cannot be empty or start with @");
            return;
        }
        
        DBObject content = context.getContent();
        
        if (content == null)
            content = new BasicDBObject();
        
        // cannot PUT an array
        if (content instanceof BasicDBList)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "data cannot be an array");
            return;
        }
        
        if (content.containsField(Relationship.RELATIONSHIPS_ELEMENT_NAME))
        {
            try
            {
                Relationship.getFromJson(content);
            }
            catch(InvalidMetadataException ex)
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "wrong relationships definition. " + ex.getMessage(), ex);
                return;
            }
        }
        
        ObjectId etag = RequestHelper.getUpdateEtag(exchange);
        
        boolean updating = context.getCollectionProps() != null;
        
        int SC = CollectionDAO.upsertCollection(context.getDBName(), context.getCollectionName(), content, etag, updating, false);
        
        exchange.setResponseCode(SC);
        
        // send the warnings if any
        if (context.getWarnings() != null && ! context.getWarnings().isEmpty())
        {
            DocumentRepresentationFactory.sendDocument(exchange.getRequestPath(), exchange, context, new BasicDBObject());
        }
        
        exchange.endExchange();
        LocalCachesSingleton.getInstance().invalidateCollection(context.getDBName(), context.getCollectionName());
    }
}
