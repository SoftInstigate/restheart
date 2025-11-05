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

import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.mongodb.db.sessions.TxnClientSessionFactory;
import org.restheart.mongodb.handlers.RequestDispatcherHandler;
import org.restheart.mongodb.handlers.injectors.ClientSessionInjector;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BootstrapLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

import static org.restheart.mongodb.ConnectionChecker.replicaSet;
import static org.restheart.mongodb.ConnectionChecker.connected;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "txnsActivator",
        description = "activates support for transactions",
        priority = Integer.MIN_VALUE + 1)
public class TxnsActivator implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TxnsActivator.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Override
    public void init() {
        if (!connected(mclient)) {
            BootstrapLogger.errorSubItem(LOGGER, "Cannot enable Transactions: MongoDB not connected.");
        } else {
            if (replicaSet(mclient)) {
                enableTxns();
            } else {
                BootstrapLogger.warnSubItem(LOGGER, "Cannot enable Transactions: MongoDB is a standalone instance and Transactions require a Replica Set.");
            }
        }
    }

    private void enableTxns() {
        var dispatcher = RequestDispatcherHandler.getInstance();

        ClientSessionInjector.getInstance().setClientSessionFactory(TxnClientSessionFactory.getInstance());

        // *** Txns handlers
        dispatcher.putHandler(TYPE.TRANSACTIONS, METHOD.POST, new PostTxnsHandler());

        dispatcher.putHandler(TYPE.TRANSACTIONS, METHOD.GET, new GetTxnHandler());

        dispatcher.putHandler(TYPE.TRANSACTION, METHOD.DELETE, new DeleteTxnHandler());

        dispatcher.putHandler(TYPE.TRANSACTION, METHOD.PATCH, new PatchTxnHandler());
    }
}
