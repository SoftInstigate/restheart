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

import com.restheart.db.txns.Txn;
import com.restheart.db.txns.TxnClientSessionFactory;
import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import org.restheart.db.Database;
import org.restheart.db.DatabaseImpl;
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
public class PatchTxnHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of PatchTxnHandler
     */
    public PatchTxnHandler() {
        super();
    }

    public PatchTxnHandler(PipedHttpHandler next) {
        super(next, new DatabaseImpl());
    }

    public PatchTxnHandler(PipedHttpHandler next, Database dbsDAO) {
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
        long txnId = context.getTxnId();

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

        // assume optimistically txn in progress, we get an error eventually
        var cs = TxnClientSessionFactory.getInstance()
                .getTxnClientSession(sid, new Txn(txnId,
                        Txn.TransactionStatus.IN));

        cs.setMessageSentInCurrentTransaction(true);

        if (!cs.hasActiveTransaction()) {
            cs.startTransaction();
        }

        cs.commitTransaction();
        context.setResponseStatusCode(HttpStatus.SC_OK);

        next(exchange, context);
    }
}
