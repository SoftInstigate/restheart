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
import org.restheart.exchange.BsonResponse;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.utils.HttpStatus;

/**
 *
 * commits the transaction of the session
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PatchTxnHandler extends PipelinedHandler {
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
        long txnId = request.getTxnId();

        UUID sid;

        try {
            sid = UUID.fromString(_sid);
        } catch (IllegalArgumentException iae) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE,
                    "Invalid session id");
            next(exchange);
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
        
        response.setContentTypeAsJson();
        response.setStatusCode(HttpStatus.SC_OK);

        next(exchange);
    }
}
