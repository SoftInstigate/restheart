/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.db.sessions;

import com.mongodb.ClientSessionOptions;
import com.mongodb.ConnectionString;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import static org.bson.assertions.Assertions.notNull;
import static org.restheart.exchange.ExchangeKeys.CLIENT_SESSION_KEY;
import static org.restheart.exchange.ExchangeKeys.TXNID_KEY;
import static org.restheart.mongodb.db.sessions.Txn.TransactionStatus.IN;

import org.restheart.mongodb.db.MongoClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class TxnClientSessionFactory extends ClientSessionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(TxnClientSessionFactory.class);

    private static ConnectionString mongoUri = null;
    private static boolean initialized = false;

    /**
     *
     * @param uri
     * @param pr
     */
    public static void init(ConnectionString uri) {
        mongoUri = uri;
        initialized = true;
    }


    public static TxnClientSessionFactory getInstance() {
        return TxnClientSessionFactoryHolder.INSTANCE;
    }

    private static class TxnClientSessionFactoryHolder {
        private static final TxnClientSessionFactory INSTANCE = new TxnClientSessionFactory();
    }

    private TxnClientSessionFactory() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
    }

    /**
     *
     * @param exchange
     * @return
     * @throws IllegalArgumentException
     */
    @Override
    public ClientSessionImpl getClientSession(HttpServerExchange exchange) throws IllegalArgumentException {
        var _sid = exchange.getQueryParameters().get(CLIENT_SESSION_KEY).getFirst();

        UUID sid;

        try {
            sid = UUID.fromString(_sid);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Invalid session id");
        }

        if (exchange.getQueryParameters().containsKey(TXNID_KEY)) {
            var _txnId = exchange.getQueryParameters().get(TXNID_KEY).getFirst();

            long txnId = -1;

            try {
                txnId = Long.parseLong(_txnId);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Invalid txn");
            }

            var cs = getTxnClientSession(sid, new Txn(txnId, Txn.TransactionStatus.IN));

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

        var cso = ClientSessionOptions
                .builder()
                .causallyConsistent(options.isCausallyConsistent())
                .build();

        var cs = createClientSession(
                sid,
                cso,
                mongoUri.getReadConcern() == null ? ReadConcern.DEFAULT : mongoUri.getReadConcern(),
                mongoUri.getWriteConcern() == null ? WriteConcern.MAJORITY : mongoUri.getWriteConcern(),
                mongoUri.getReadPreference() == null ? ReadPreference.primary() : mongoUri.getReadPreference(),
                null);

        if (txnServerStatus != null) {
            cs.setTxnServerStatus(txnServerStatus);
            cs.setTransactionState(txnServerStatus.getStatus());
            cs.setServerSessionTransactionNumber(txnServerStatus.getTxnId());

            ((ServerSessionImpl) cs.getServerSession()).setTransactionNumber(txnServerStatus.getTxnId());
        }

        return cs;
    }

    TxnClientSessionImpl createClientSession(UUID sid, final ClientSessionOptions options) {
        return createClientSession(
            sid,
            options,
            mongoUri.getReadConcern() == null ? ReadConcern.DEFAULT : mongoUri.getReadConcern(),
                mongoUri.getWriteConcern() == null ? WriteConcern.MAJORITY : mongoUri.getWriteConcern(),
                mongoUri.getReadPreference() == null ? ReadPreference.primary() : mongoUri.getReadPreference(),
            null);
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

        var mergedOptions = ClientSessionOptions
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
                MongoClientSingleton.getInstance().getClient(),
                mergedOptions,
                SessionsUtils.getOperationExecutor(),
                txnServerStatus);
    }
}
