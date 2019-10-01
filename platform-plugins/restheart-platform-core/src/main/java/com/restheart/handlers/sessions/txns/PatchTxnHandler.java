/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
