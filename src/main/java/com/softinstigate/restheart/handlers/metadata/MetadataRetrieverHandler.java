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
package com.softinstigate.restheart.handlers.metadata;

import com.mongodb.DBCollection;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.Map;

/**
 *
 * @author uji
 */
public class MetadataRetrieverHandler extends PipedHttpHandler
{
    /**
     * Creates a new instance of GetCollectionHandler
     *
     * @param next
     */
    public MetadataRetrieverHandler(PipedHttpHandler next)
    {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (context.getDBName() != null)
        {
            List<String> colls = DBDAO.getDbCollections(DBDAO.getDB(context.getDBName()));

            Map<String, Object> dbMetadata = DBDAO.getDbMetaData(context.getDBName(), colls);

            context.setDbMetadata(dbMetadata);
        }

        if (context.getDBName() != null && context.getCollectionName() != null)
        {
            DBCollection coll = CollectionDAO.getCollection(context.getDBName(), context.getCollectionName());

            Map<String, Object> collMetadata = null;

            collMetadata = CollectionDAO.getCollectionMetadata(coll);

            context.setCollectionMetadata(collMetadata);
        }

        next.handleRequest(exchange, context);
    }
}
