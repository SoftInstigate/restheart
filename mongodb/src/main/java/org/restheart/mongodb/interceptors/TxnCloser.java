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
package org.restheart.mongodb.interceptors;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.handlers.injectors.ClientSessionInjector;
import org.restheart.mongodb.db.sessions.TxnClientSessionImpl;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoException;

/**
 * Closes the transaction a request runs in: commits it, or aborts it when an interceptor asked to
 * undo the write with {@code MongoResponse.rollback()}.
 *
 * <p>A plugin never commits. It declares at request time, with {@code MongoRequest.startTxn()},
 * that it may need to undo its write; the commit happens here, once every other {@code RESPONSE}
 * interceptor has run. That is why this runs at {@code Integer.MAX_VALUE}: an interceptor that
 * decides to roll back has to have run already, and one that runs after a commit would be looking
 * at a decision it can no longer influence.
 *
 * <p>It is also why {@code rollback()} marks rather than aborts. Aborting on the spot would leave
 * every later interceptor working on a dead session, and no single interceptor can know whether
 * another one also wants to abort.
 */
@RegisterPlugin(
        name = "txnCloser",
        description = "commits the transaction of a request that asked for one, or aborts it on rollback",
        interceptPoint = InterceptPoint.RESPONSE,
        // run after every other response interceptor: they are the ones that may ask to roll back
        priority = Integer.MAX_VALUE)
public class TxnCloser implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TxnCloser.class);

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        if (!(request.getClientSession() instanceof TxnClientSessionImpl txn)) {
            return;
        }

        if (response.isRollbackRequested()) {
            abort(txn, request);
        } else if (ClientSessionInjector.serverStartedTxn(request)) {
            commit(txn, response);
        }
        // else: the transaction belongs to a client driving it with ?sid=&txn=, and committing it
        // here would end it after this one write — the rest of what the client meant to put in it
        // would then fail with "transaction already committed". We only ever abort someone else's.
    }

    /**
     * Aborting a transaction the client owns ({@code ?sid=&txn=}) discards the client's whole
     * transaction, not just this write. That is the only safe outcome: leaving a refused document
     * in an uncommitted transaction the client can still commit would defeat the check that refused
     * it. The interceptor that called {@code rollback()} has already put the reason in the response.
     */
    private void abort(TxnClientSessionImpl txn, MongoRequest request) {
        txn.abortTransaction();

        if (request.isTxnRequested()) {
            LOGGER.debug("Transaction aborted, the write was rolled back");
        } else {
            LOGGER.debug("Client transaction {} aborted: a write in it was rolled back", request.getTxnId());
        }
    }

    /**
     * A failed commit is the one case where the response already says the write succeeded and it
     * did not, so the status has to be rewritten. A transient error means a concurrent write to the
     * same document won the race; nothing was written and the client can repeat the request.
     */
    private void commit(TxnClientSessionImpl txn, MongoResponse response) {
        try {
            txn.commitTransaction();
        } catch (final MongoException me) {
            LOGGER.warn("Could not commit the transaction, the write was discarded", me);

            if (me.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                response.setInError(HttpStatus.SC_CONFLICT,
                        "the write conflicted with a concurrent one and was not applied, retry the request");
            } else {
                response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "the write could not be committed and was not applied");
            }
        }
    }

    /**
     * Only for a transaction that is still open. A request whose {@code startTxn()} found no
     * replica set has no session and is skipped: there {@code rollback()} compensates instead.
     */
    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.getClientSession() != null
                && request.getClientSession().hasActiveTransaction()
                && (request.isTxnRequested() || response.isRollbackRequested());
    }
}
