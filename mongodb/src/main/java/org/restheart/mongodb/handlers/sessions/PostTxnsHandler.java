/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.handlers.sessions;

import java.util.UUID;

import org.bson.BsonString;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.sessions.Txn;
import static org.restheart.mongodb.db.sessions.Txn.TransactionStatus.ABORTED;
import static org.restheart.mongodb.db.sessions.Txn.TransactionStatus.COMMITTED;
import static org.restheart.mongodb.db.sessions.Txn.TransactionStatus.NONE;
import org.restheart.mongodb.db.sessions.TxnClientSessionFactory;
import org.restheart.mongodb.db.sessions.TxnsUtils;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.RepresentationUtils.getReferenceLink;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

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
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        var _sid = request.getSid();

        UUID sid;

        try {
            sid = UUID.fromString(_sid);
        } catch (IllegalArgumentException iae) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, "Invalid session id");
            next(exchange);
            return;
        }

        var txn = TxnsUtils.getTxnServerStatus(sid, request.rsOps());

        if (txn.getStatus() == ABORTED || txn.getStatus() == COMMITTED || txn.getStatus() == NONE) {
            var nextTxnId = txn.getStatus() == NONE ? txn.getTxnId() : txn.getTxnId() + 1;

            var cs = TxnClientSessionFactory.getInstance().getTxnClientSession(sid, request.rsOps(), new Txn(nextTxnId, txn.getStatus()));

            cs.setMessageSentInCurrentTransaction(false);

            if (!cs.hasActiveTransaction()) {
                cs.startTransaction();
            }

            // propagate the transaction
            TxnsUtils.propagateSession(cs);

            response.getHeaders().add(HttpString.tryFromString("Location"), getReferenceLink(request.getMongoResourceUri(), new BsonString("" + nextTxnId)));

            response.setStatusCode(HttpStatus.SC_CREATED);
        } else {
            response.setStatusCode(HttpStatus.SC_NOT_MODIFIED);
        }

        next(exchange);
    }
}
