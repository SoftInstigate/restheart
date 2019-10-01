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
