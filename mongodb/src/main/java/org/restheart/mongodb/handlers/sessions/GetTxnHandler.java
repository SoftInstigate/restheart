/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import static org.restheart.mongodb.db.sessions.Txn.TransactionStatus.NONE;
import org.restheart.mongodb.db.sessions.TxnsUtils;
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
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "Invalid session id");
            next(exchange);
            return;
        }

        var txn = TxnsUtils.getTxnServerStatus(sid, request.rsOps());

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
