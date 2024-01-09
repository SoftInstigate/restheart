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
package org.restheart.mongodb.db.sessions;

import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoClientException;
import com.mongodb.MongoInternalException;
import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.internal.OperationExecutor;
import com.mongodb.internal.session.ServerSessionPool;
import com.mongodb.internal.operation.AbortTransactionOperation;
import com.mongodb.internal.operation.CommitTransactionOperation;
import static org.bson.assertions.Assertions.isTrue;
import static org.bson.assertions.Assertions.notNull;
import org.restheart.mongodb.db.sessions.Txn.TransactionStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class TxnClientSessionImpl extends ClientSessionImpl {
    private final OperationExecutor executor;
    private TransactionStatus transactionState = TransactionStatus.NONE;
    private boolean commitInProgress;
    private TransactionOptions transactionOptions;
    private Txn txnServerStatus = null;

    public TxnClientSessionImpl(final ServerSessionPool serverSessionPool,
        final Object originator,
        final ClientSessionOptions options,
        final OperationExecutor executor,
        final Txn txnServerStatus) {
        super(serverSessionPool, originator, options);
        this.executor = executor;
        this.txnServerStatus = txnServerStatus;
    }

    public void setMessageSentInCurrentTransaction(boolean messageSentInCurrentTransaction) {
        this.messageSentInCurrentTransaction = messageSentInCurrentTransaction;
    }

    public boolean isMessageSentInCurrentTransaction() {
        return this.messageSentInCurrentTransaction;
    }

    @Override
    public boolean hasActiveTransaction() {
        return transactionState == TransactionStatus.IN || (transactionState == TransactionStatus.COMMITTED && commitInProgress);
    }

    public boolean isTransacted() {
        return txnServerStatus != null;
    }

    @Override
    public boolean notifyMessageSent() {
        if (hasActiveTransaction()) {
            boolean firstMessageInCurrentTransaction = !messageSentInCurrentTransaction;
            messageSentInCurrentTransaction = true;
            return firstMessageInCurrentTransaction;
        } else {
            if (transactionState == TransactionStatus.COMMITTED || transactionState == TransactionStatus.ABORTED) {
                cleanupTransaction(TransactionStatus.NONE);
            }
            return false;
        }
    }

    @Override
    public TransactionOptions getTransactionOptions() {
        isTrue("in transaction", transactionState == TransactionStatus.IN || transactionState == TransactionStatus.COMMITTED);
        return transactionOptions;
    }

    @Override
    public void startTransaction() {
        startTransaction(TransactionOptions.builder().build());
    }

    @Override
    public void startTransaction(final TransactionOptions transactionOptions) {
        notNull("transactionOptions", transactionOptions);
        if (transactionState == TransactionStatus.IN) {
            throw new IllegalStateException("Transaction already in progress");
        }
        if (transactionState == TransactionStatus.COMMITTED) {
            cleanupTransaction(TransactionStatus.IN);
        } else {
            transactionState = TransactionStatus.IN;
        }

        //getServerSession().advanceTransactionNumber();
        this.transactionOptions = TransactionOptions.merge(transactionOptions, getOptions().getDefaultTransactionOptions());

        WriteConcern writeConcern = this.transactionOptions.getWriteConcern();
        if (writeConcern == null) {
            throw new MongoInternalException("Invariant violated. Transaction options write concern can not be null");
        }
        if (!writeConcern.isAcknowledged()) {
            throw new MongoClientException("Transactions do not support unacknowledged write concern");
        }
    }

    public void setServerSessionTransactionNumber(long number) {
        ((ServerSessionImpl) getServerSession()).setTransactionNumber(number);
    }

    public void setTransactionState(TransactionStatus transactionState) {
        startTransaction(); // this inits the transactionOptions
        this.transactionState = transactionState;
    }

    @Override
    public void commitTransaction() {
        if (transactionState == TransactionStatus.ABORTED) {
            throw new IllegalStateException("Cannot call commitTransaction after calling abortTransaction");
        }
        if (transactionState == TransactionStatus.NONE) {
            throw new IllegalStateException("There is no transaction started");
        }
        try {
            if (messageSentInCurrentTransaction) {
                ReadConcern readConcern = transactionOptions.getReadConcern();
                if (readConcern == null) {
                    throw new MongoInternalException("Invariant violated. Transaction options read concern can not be null");
                }
                commitInProgress = true;
                executor.execute(new CommitTransactionOperation(transactionOptions.getWriteConcern()), readConcern, this);
            }
        } finally {
            commitInProgress = false;
            transactionState = TransactionStatus.COMMITTED;
        }
    }

    @Override
    public void abortTransaction() {
        if (transactionState == TransactionStatus.ABORTED) {
            throw new IllegalStateException("Cannot call abortTransaction twice");
        }
        if (transactionState == TransactionStatus.COMMITTED) {
            throw new IllegalStateException("Cannot call abortTransaction after calling commitTransaction");
        }
        if (transactionState == TransactionStatus.NONE) {
            throw new IllegalStateException("There is no transaction started");
        }
        try {
            if (messageSentInCurrentTransaction) {
                ReadConcern readConcern = transactionOptions.getReadConcern();
                if (readConcern == null) {
                    throw new MongoInternalException("Invariant violated. Transaction options read concern can not be null");
                }
                executor.execute(new AbortTransactionOperation(transactionOptions.getWriteConcern()), readConcern, this);
            }
        } catch (Exception e) {
            // ignore errors
        } finally {
            cleanupTransaction(TransactionStatus.ABORTED);
        }
    }

    @Override
    public void close() {
        try {
            if (transactionState == TransactionStatus.IN) {
                abortTransaction();
            }
        } finally {
            // this release the session from the pool,
            // not required in our implementation
            // super.close();
        }
    }

    private void cleanupTransaction(final TransactionStatus nextState) {
        messageSentInCurrentTransaction = false;
        transactionOptions = null;
        transactionState = nextState;
    }

    /**
     * @return the txnServerStatus
     */
    public Txn getTxnServerStatus() {
        return txnServerStatus;
    }

    /**
     * @param txnServerStatus the txnServerStatus to set
     */
    public void setTxnServerStatus(Txn txnServerStatus) {
        this.txnServerStatus = txnServerStatus;
    }
}
