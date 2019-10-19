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
package com.restheart.handlers.sessions.txns;

import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.restheart.db.Database;
import org.restheart.db.DatabaseImpl;
import static com.restheart.db.txns.Txn.TransactionStatus.NONE;
import com.restheart.db.txns.TxnsUtils;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * commits the transaction of the session
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetTxnHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of PatchTxnHandler
     */
    public GetTxnHandler() {
        super();
    }

    public GetTxnHandler(PipedHttpHandler next) {
        super(next, new DatabaseImpl());
    }

    public GetTxnHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }
        
        String _sid = context.getSid();

        UUID sid;

        try {
            sid = UUID.fromString(_sid);
        } catch (IllegalArgumentException iae) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "Invalid session id");
            next(exchange, context);
            return;
        }

        var txn = TxnsUtils.getTxnServerStatus(sid);

        if (txn.getStatus() == NONE) {
            context.setResponseContent(
                    new BsonDocument("currentTxn", new BsonNull()));
        } else {
            var currentTxn = new BsonDocument();

            var resp = new BsonDocument("currentTxn", currentTxn);

            currentTxn.append("id",
                    txn.getTxnId() > Integer.MAX_VALUE
                    ? new BsonInt64(txn.getTxnId())
                    : new BsonInt32((int) txn.getTxnId()));

            currentTxn.append("status", new BsonString(txn.getStatus().name()));
            
            context.setResponseContent(resp);
        }

        context.setResponseStatusCode(HttpStatus.SC_OK);

        next(exchange, context);
    }

}
