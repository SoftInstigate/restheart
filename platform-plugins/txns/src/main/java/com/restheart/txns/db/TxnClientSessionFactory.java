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
package com.restheart.txns.db;

import com.mongodb.ClientSessionOptions;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import static com.restheart.txns.db.Txn.TransactionStatus.IN;
import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import static org.bson.assertions.Assertions.notNull;
import static org.restheart.exchange.ExchangeKeys.CLIENT_SESSION_KEY;
import static org.restheart.exchange.ExchangeKeys.TXNID_KEY;
import org.restheart.mongodb.db.sessions.ClientSessionFactory;
import org.restheart.mongodb.db.sessions.ClientSessionImpl;
import org.restheart.mongodb.db.sessions.ServerSessionImpl;
import org.restheart.mongodb.db.sessions.SessionsUtils;
import org.restheart.mongodb.db.sessions.Sid;
import org.restheart.mongodb.db.sessions.SimpleServerSessionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class TxnClientSessionFactory extends ClientSessionFactory {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(TxnClientSessionFactory.class);

    public static TxnClientSessionFactory getInstance() {
        return TxnClientSessionFactoryHolder.INSTANCE;
    }

    private static class TxnClientSessionFactoryHolder {
        private static final TxnClientSessionFactory INSTANCE
                = new TxnClientSessionFactory();
    }

    private TxnClientSessionFactory() {
    }

    /**
     *
     * @param exchange
     * @return
     * @throws IllegalArgumentException
     */
    @Override
    public ClientSessionImpl getClientSession(HttpServerExchange exchange)
            throws IllegalArgumentException {
        String _sid = exchange.getQueryParameters()
                .get(CLIENT_SESSION_KEY).getFirst();

        UUID sid;
        
        try {
            sid = UUID.fromString(_sid);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Invalid session id");
        }

        if (exchange.getQueryParameters()
                .containsKey(TXNID_KEY)) {
            String _txnId = exchange.getQueryParameters()
                    .get(TXNID_KEY).getFirst();

            long txnId = -1;

            try {
                txnId = Long.parseLong(_txnId);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Invalid txn");
            }

            var cs = getTxnClientSession(sid,
                    new Txn(txnId, Txn.TransactionStatus.IN));

            LOGGER.debug("Request is executed in session {} with {}",
                    _sid,
                    cs.getTxnServerStatus());

            if (cs.getTxnServerStatus().getStatus() == IN) {
                cs.setMessageSentInCurrentTransaction(true);

                if (!cs.hasActiveTransaction()) {
                    cs.startTransaction();
                }
            }

            return cs;
        } else {
            LOGGER.debug("Request is executed in session {}", _sid);

            return super.getClientSession(sid);

        }
    }

    /**
     *
     * Warn: requires a round trip to the server
     *
     * @param sid
     * @return
     */
    public TxnClientSessionImpl getTxnClientSession(UUID sid) {
        return getTxnClientSession(sid, TxnsUtils.getTxnServerStatus(sid));
    }

    /**
     *
     * @param sid
     * @param txnServerStatus
     * @return
     */
    public TxnClientSessionImpl getTxnClientSession(UUID sid, Txn txnServerStatus) {
        var options = Sid.getSessionOptions(sid);

        ClientSessionOptions cso = ClientSessionOptions
                .builder()
                .causallyConsistent(options.isCausallyConsistent())
                .build();

        var cs = createClientSession(
                sid,
                cso,
                mClient.getReadConcern(),
                mClient.getWriteConcern(),
                mClient.getReadPreference(),
                null);

        if (txnServerStatus != null) {
            cs.setTxnServerStatus(txnServerStatus);
            cs.setTransactionState(txnServerStatus.getStatus());
            cs.setServerSessionTransactionNumber(txnServerStatus.getTxnId());

            ((ServerSessionImpl) cs.getServerSession())
                    .setTransactionNumber(txnServerStatus.getTxnId());
        }

        return cs;
    }

    TxnClientSessionImpl createClientSession(
            UUID sid,
            final ClientSessionOptions options,
            final ReadConcern readConcern,
            final WriteConcern writeConcern,
            final ReadPreference readPreference,
            final Txn txnServerStatus) {
        notNull("readConcern", readConcern);
        notNull("writeConcern", writeConcern);
        notNull("readPreference", readPreference);

        // TODO allow request to specify session and txn options
        ClientSessionOptions mergedOptions = ClientSessionOptions
                .builder(options)
                .causallyConsistent(true)
                .defaultTransactionOptions(
                        TransactionOptions.merge(
                                options.getDefaultTransactionOptions(),
                                TransactionOptions.builder()
                                        .readConcern(readConcern)
                                        .writeConcern(writeConcern)
                                        .readPreference(readPreference)
                                        .build()))
                .build();

        return new TxnClientSessionImpl(
                new SimpleServerSessionPool(SessionsUtils.getCluster(), sid),
                mClient,
                mergedOptions,
                SessionsUtils.getMongoClientDelegate(),
                txnServerStatus);
    }
}
