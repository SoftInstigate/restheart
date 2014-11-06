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
import com.softinstigate.restheart.db.DocumentDAO;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.handlers.document.DocumentRepresentationFactory;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import org.bson.types.ObjectId;

/**
 *
 * @author uji
 */
public class PostCollectionHandler extends PutCollectionHandler
{
    /**
     * Creates a new instance of PostCollectionHandler
     */
    public PostCollectionHandler()
    {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        DBObject content = context.getContent();

        if (content == null)
        {
            content = new BasicDBObject();
        }

        // cannot POST an array
        if (content instanceof BasicDBList)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "data cannot be an array");
            return;
        }

        ObjectId etag = RequestHelper.getWriteEtag(exchange);

        if (content.get("_id") != null)
        {
            if (content.get("_id") instanceof String && RequestContext.isReservedResourceDocument((String)content.get("_id")))
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "reserved resource");
                return;
            }
        }

        int SC = DocumentDAO.upsertDocumentPost(exchange, context.getDBName(), context.getCollectionName(), content, etag);

        // send the warnings if any (and in case no_content change the return code to ok
        if (context.getWarnings() != null && !context.getWarnings().isEmpty())
        {
            if (SC == HttpStatus.SC_NO_CONTENT)
            {
                exchange.setResponseCode(HttpStatus.SC_OK);
            }
            else
            {
                exchange.setResponseCode(SC);
            }

            DocumentRepresentationFactory.sendDocument(exchange.getRequestPath(), exchange, context, new BasicDBObject());
        }
        else
        {
            exchange.setResponseCode(SC);
        }

        exchange.endExchange();
    }
}
