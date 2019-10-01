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
