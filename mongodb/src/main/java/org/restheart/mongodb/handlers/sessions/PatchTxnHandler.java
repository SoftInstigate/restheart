/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.sessions.Txn;
import org.restheart.mongodb.db.sessions.TxnClientSessionFactory;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;

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
            response.setInError(HttpStatus.SC_BAD_REQUEST, "Invalid session id");
            next(exchange);
            return;
        }

        // assume optimistically txn in progress, we get an error eventually
        var cs = TxnClientSessionFactory.getInstance()
                .getTxnClientSession(sid, request.rsOps(), new Txn(txnId, Txn.TransactionStatus.IN));

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
