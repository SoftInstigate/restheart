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
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.List;

/**
 *
 * @author Andrea Di Cesare
 */
public class GetDBHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of GetDBHandler
     */
    public GetDBHandler() {
        super(null);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        List<String> colls = DBDAO.getDbCollections(DBDAO.getDB(context.getDBName()));

        List<DBObject> data = DBDAO.getData(context.getDBName(), colls, context.getPage(), context.getPagesize());

        exchange.setResponseCode(HttpStatus.SC_OK);
        DBRepresentationFactory.sendHal(exchange, context, data, DBDAO.getDBSize(colls));
        exchange.endExchange();
    }
}
