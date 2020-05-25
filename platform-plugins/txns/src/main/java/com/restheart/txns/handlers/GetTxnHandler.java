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

import static com.restheart.txns.db.Txn.TransactionStatus.NONE;
import com.restheart.txns.db.TxnsUtils;
import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
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
public class GetTxnHandler extends PipelinedHandler {
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

        if (txn.getStatus() == NONE) {
            response.setContent(new BsonDocument("currentTxn", new BsonNull()));
        } else {
            var currentTxn = new BsonDocument();

            var resp = new BsonDocument("currentTxn", currentTxn);

            currentTxn.append("id",
                    txn.getTxnId() > Integer.MAX_VALUE
                    ? new BsonInt64(txn.getTxnId())
                    : new BsonInt32((int) txn.getTxnId()));

            currentTxn.append("status", new BsonString(txn.getStatus().name()));
            
            response.setContent(resp);
        }

        response.setContentTypeAsJson();
        response.setStatusCode(HttpStatus.SC_OK);

        next(exchange);
    }
}
