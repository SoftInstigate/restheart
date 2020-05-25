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
package com.restheart.txns.handlers;

import com.restheart.txns.db.Txn;
import static com.restheart.txns.db.Txn.TransactionStatus.ABORTED;
import static com.restheart.txns.db.Txn.TransactionStatus.COMMITTED;
import static com.restheart.txns.db.Txn.TransactionStatus.NONE;
import com.restheart.txns.db.TxnClientSessionFactory;
import com.restheart.txns.db.TxnsUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.UUID;
import org.bson.BsonString;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.representation.RepresentationUtils;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.URLUtils;

/**
 *
 * creates a session with a started transaction
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PostTxnsHandler extends PipelinedHandler {
    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange)
            throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);
        
        if (request.isInError()) {
            next(exchange);
            return;
        }
        
        String _sid = request.getSid();

        UUID sid;

        try {
            sid = UUID.fromString(_sid);
        } catch (IllegalArgumentException iae) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE,
                    "Invalid session id");
            next(exchange);
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
                            RepresentationUtils.getReferenceLink(
                                    request.getMappedRequestUri(), //URLUtils.getRemappedRequestURL(exchange),
                                    new BsonString("" + nextTxnId)));

            response.setStatusCode(HttpStatus.SC_CREATED);
        } else {
            response.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
        }

        next(exchange);
    }
}
