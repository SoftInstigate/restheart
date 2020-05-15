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
import com.restheart.db.txns.TxnClientSessionImpl;
import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import org.restheart.db.Database;
import org.restheart.db.DatabaseImpl;
import org.restheart.representation.Resource;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * aborts transaction of the session
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteTxnHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of DeleteTxnHandler
     */
    public DeleteTxnHandler() {
        super();
    }

    public DeleteTxnHandler(PipedHttpHandler next) {
        super(next, new DatabaseImpl());
    }

    public DeleteTxnHandler(PipedHttpHandler next, Database dbsDAO) {
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
        
        UUID sid;
        
        try {
            sid = UUID.fromString(context.getSid());
        } catch (IllegalArgumentException iae) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "Invalid session id");
            next(exchange, context);
            return;
        }

        TxnClientSessionImpl cs = TxnClientSessionFactory.getInstance()
                .getTxnClientSession(sid);

        if (cs.getTxnServerStatus().getTxnId() != context.getTxnId()
                || cs.getTxnServerStatus().getStatus() != Txn.TransactionStatus.IN) {
            ResponseHelper.endExchangeWithMessage(exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "The given transaction is not in-progress");
        } else {
            cs.setMessageSentInCurrentTransaction(true);
            cs.abortTransaction();

            context.setResponseContentType(Resource.HAL_JSON_MEDIA_TYPE);
            context.setResponseStatusCode(HttpStatus.SC_NO_CONTENT);
        }

        next(exchange, context);
    }
}
