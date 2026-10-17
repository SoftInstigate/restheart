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
package org.restheart.mongodb.handlers.injectors;

import static org.restheart.exchange.ExchangeKeys.CLIENT_SESSION_KEY;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.sessions.ClientSessionFactory;
import org.restheart.mongodb.db.sessions.Sid;
import org.restheart.mongodb.db.sessions.Txn;
import org.restheart.mongodb.db.sessions.TxnClientSessionFactory;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 *
 * this handler injects the ClientSession in the request context
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ClientSessionInjector extends PipelinedHandler {

    /**
     *
     * @return
     */
    public static ClientSessionInjector getInstance() {
        if (ClientSessionInjectorHandlerHolder.INSTANCE == null) {
            throw new IllegalStateException("Singleton not initialized");
        }

        return ClientSessionInjectorHandlerHolder.INSTANCE;
    }

    private static class ClientSessionInjectorHandlerHolder {
        private static ClientSessionInjector INSTANCE = null;
    }

    /**
     *
     * @return
     */
    public static ClientSessionInjector build() {
        if (ClientSessionInjectorHandlerHolder.INSTANCE != null) {
            throw new IllegalStateException("Singleton already initialized");
        }

        ClientSessionInjectorHandlerHolder.INSTANCE = new ClientSessionInjector();

        return ClientSessionInjectorHandlerHolder.INSTANCE;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSessionInjector.class);

    /**
     * Marks a transaction this server opened, as opposed to one the client owns and drives with
     * {@code ?sid=&txn=}. Only the former is ours to commit.
     */
    private static final AttachmentKey<Boolean> SERVER_STARTED_TXN = AttachmentKey.create(Boolean.class);

    /**
     * @return true if the transaction the request runs in was opened by this server, and is
     * therefore this server's to commit
     */
    public static boolean serverStartedTxn(MongoRequest request) {
        return Boolean.TRUE.equals(request.getExchange().getAttachment(SERVER_STARTED_TXN));
    }

    private ClientSessionFactory clientSessionFactory = ClientSessionFactory.getInstance();

    /**
     * Creates a new instance of DbPropsInjectorHandler
     *
     * @param next
     */
    private ClientSessionInjector() {
        this(null);
    }

    /**
     * Creates a new instance of DbPropsInjectorHandler
     *
     * @param next
     */
    private ClientSessionInjector(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        if (exchange.getQueryParameters().containsKey(CLIENT_SESSION_KEY)) {
            try {
                request.setClientSession(getClientSessionFactory().getClientSession(exchange));
            } catch (IllegalArgumentException ex) {
                MongoResponse.of(exchange).setInError(HttpStatus.SC_BAD_REQUEST, ex.getMessage());
                next(exchange);
                return;
            }
        } else if (request.isTxnRequested()) {
            startServerTxn(request);
        }

        next(exchange);
    }

    /**
     * Opens a transaction for a request that asked, with {@code MongoRequest.startTxn()}, to be able
     * to undo its own write. The write joins it: every handler passes
     * {@code request.getClientSession()} down to the db layer.
     *
     * <p>Only reached when the client did not supply a session of its own. A client-driven
     * transaction ({@code ?sid=&txn=}) is joined rather than nested — MongoDB has no nested
     * transactions — and it is the branch above that installs it.
     *
     * <p>Whether transactions are available at all is read from the installed factory rather than
     * probed here: {@code TxnsActivator} swaps in {@link TxnClientSessionFactory} at startup, and
     * only when MongoDB is a replica set. When it did not, this is a no-op and the request runs
     * unwrapped — {@code rollback()} then falls back to a compensating write.
     */
    private void startServerTxn(MongoRequest request) {
        if (!(getClientSessionFactory() instanceof TxnClientSessionFactory txnFactory)) {
            LOGGER.debug("Transaction requested but not available: MongoDB is not a replica set. "
                    + "A rollback would be a compensating write.");
            return;
        }

        // a session of our own, used by this request alone, so its transaction number starts at 1
        var cs = txnFactory.getTxnClientSession(Sid.randomUUID(), request.rsOps(), new Txn(1, Txn.TransactionStatus.NONE));

        cs.setMessageSentInCurrentTransaction(false);

        if (!cs.hasActiveTransaction()) {
            cs.startTransaction();
        }

        request.setClientSession(cs);
        request.getExchange().putAttachment(SERVER_STARTED_TXN, Boolean.TRUE);

        // Safety net for the one path that skips txnCloser: a RESPONSE interceptor that throws
        // stops the executor's loop, so the interceptors after it — txnCloser included — never run.
        // close() aborts a transaction still in progress and swallows its own errors, so a request
        // that blew up leaves nothing open and nothing committed. On every normal request the
        // transaction is already closed by then and this is a no-op.
        request.getExchange().addExchangeCompleteListener((exchange, nextListener) -> {
            try {
                if (cs.hasActiveTransaction()) {
                    LOGGER.warn("Transaction still open when the request ended, aborting it: "
                            + "the write was not committed");
                    cs.close();
                }
            } finally {
                nextListener.proceed();
            }
        });

        LOGGER.debug("Request runs in a server-side transaction");
    }

    /**
     * Whether this deployment can run a request in a transaction.
     *
     * <p>Read from the installed factory rather than probed: {@code TxnsActivator} swaps in
     * {@link TxnClientSessionFactory} at startup, and only when MongoDB is a replica set.
     *
     * @return true if {@code MongoRequest.startTxn()} will actually open a transaction
     */
    public static boolean transactionsAvailable() {
        return getInstance().getClientSessionFactory() instanceof TxnClientSessionFactory;
    }

    /**
     * @return the clientSessionFactory
     */
    public ClientSessionFactory getClientSessionFactory() {
        return clientSessionFactory;
    }

    /**
     * @param clientSessionFactory the clientSessionFactory to set
     */
    public void setClientSessionFactory(ClientSessionFactory clientSessionFactory) {
        this.clientSessionFactory = clientSessionFactory;
    }
}
