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
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.UUID;
import org.bson.BsonString;
import org.restheart.db.Database;
import org.restheart.db.DatabaseImpl;
import static com.restheart.db.txns.Txn.TransactionStatus.ABORTED;
import static com.restheart.db.txns.Txn.TransactionStatus.COMMITTED;
import static com.restheart.db.txns.Txn.TransactionStatus.NONE;
import com.restheart.db.txns.TxnClientSessionFactory;
import com.restheart.db.txns.TxnsUtils;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.representation.RepUtils;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;

/**
 *
 * creates a session with a started transaction
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PostTxnsHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of PostTxnsHandler
     */
    public PostTxnsHandler() {
        super();
    }

    public PostTxnsHandler(PipedHttpHandler next) {
        super(next, new DatabaseImpl());
    }

    public PostTxnsHandler(PipedHttpHandler next, Database dbsDAO) {
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

        if (txn.getStatus() == ABORTED
                || txn.getStatus() == COMMITTED
                || txn.getStatus() == NONE) {
            var nextTxnId = txn.getStatus() == NONE
                    ? txn.getTxnId()
                    : txn.getTxnId() + 1;

            var cs = TxnClientSessionFactory.getInstance()
                    .getTxnClientSession(sid, new Txn(nextTxnId, txn.getStatus()));

            cs.setMessageSentInCurrentTransaction(false);

            if (!cs.hasActiveTransaction()) {
                cs.startTransaction();
            }

            // propagate the transaction
            TxnsUtils.propagateSession(cs);

            exchange.getResponseHeaders()
                    .add(HttpString.tryFromString("Location"),
                            RepUtils.getReferenceLink(
                                    context,
                                    URLUtils.getRemappedRequestURL(exchange),
                                    new BsonString("" + nextTxnId)));

            context.setResponseStatusCode(HttpStatus.SC_CREATED);
        } else {
            context.setResponseStatusCode(HttpStatus.SC_NOT_MODIFIED);
        }

        next(exchange, context);
    }
}
