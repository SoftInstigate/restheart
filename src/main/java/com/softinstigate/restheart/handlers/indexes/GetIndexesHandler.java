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

import com.mongodb.DBObject;
import com.softinstigate.restheart.db.IndexDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import java.util.List;

/**
 *
 * @author uji
 */
public class GetIndexesHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of GetIndexesHandler
     */
    public GetIndexesHandler() {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        List<DBObject> indexes = IndexDAO.getCollectionIndexes(context.getDBName(), context.getCollectionName());

        exchange.setResponseCode(HttpStatus.SC_OK);
        IndexesRepresentationFactory.sendHal(exchange, context, indexes, indexes.size());
        exchange.endExchange();
    }
}
