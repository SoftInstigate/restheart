/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.sessions.Txn;
import org.restheart.mongodb.db.sessions.TxnClientSessionFactory;
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

        var cs = TxnClientSessionFactory.getInstance().getTxnClientSession(sid, request.rsOps());

        if (cs.getTxnServerStatus().getTxnId() != request.getTxnId() || cs.getTxnServerStatus().getStatus() != Txn.TransactionStatus.IN) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "The given transaction is not in-progress");
        } else {
            cs.setMessageSentInCurrentTransaction(true);
            cs.abortTransaction();

            response.setContentTypeAsJson();
            response.setStatusCode(HttpStatus.SC_NO_CONTENT);
        }

        next(exchange);
    }
}
