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
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.utils.HttpStatus;

/**
 *
 * aborts transaction of the session
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteTxnHandler extends PipelinedHandler {
    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);
        
        if (request.isInError()) {
            next(exchange);
            return;
        }
        
        UUID sid;
        
        try {
            sid = UUID.fromString(request.getSid());
        } catch (IllegalArgumentException iae) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "Invalid session id");
            next(exchange);
            return;
        }

        TxnClientSessionImpl cs = TxnClientSessionFactory.getInstance()
                .getTxnClientSession(sid);

        if (cs.getTxnServerStatus().getTxnId() != request.getTxnId()
                || cs.getTxnServerStatus().getStatus() != Txn.TransactionStatus.IN) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE,
                    "The given transaction is not in-progress");
        } else {
            cs.setMessageSentInCurrentTransaction(true);
            cs.abortTransaction();
            
            response.setContentTypeAsJson();
            response.setStatusCode(HttpStatus.SC_NO_CONTENT);
        }

        next(exchange);
    }
}
