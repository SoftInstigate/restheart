/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.db.sessions;

import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoClientException;
import com.mongodb.MongoInternalException;
import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.internal.MongoClientDelegate;
import com.mongodb.internal.session.BaseClientSessionImpl;
import com.mongodb.internal.session.ServerSessionPool;
import com.mongodb.operation.AbortTransactionOperation;
import com.mongodb.operation.CommitTransactionOperation;
import java.util.Objects;
import static org.bson.assertions.Assertions.isTrue;
import static org.bson.assertions.Assertions.notNull;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ClientSessionImpl
        extends BaseClientSessionImpl
        implements ClientSession {

    public enum TransactionState {
        NONE, IN, COMMITTED, ABORTED
    }

    private final MongoClientDelegate delegate;
    private TransactionState transactionState = TransactionState.NONE;
    private boolean messageSentInCurrentTransaction;
    private boolean commitInProgress;
    private TransactionOptions transactionOptions;

    public ClientSessionImpl(final ServerSessionPool serverSessionPool,
            final Object originator,
            final ClientSessionOptions options,
            final MongoClientDelegate delegate) {
        super(serverSessionPool, originator, options);
        this.delegate = delegate;
    }

    public void setMessageSentInCurrentTransaction(
            boolean messageSentInCurrentTransaction) {
        this.messageSentInCurrentTransaction = messageSentInCurrentTransaction;
    }

    @Override
    public boolean hasActiveTransaction() {
        return transactionState == TransactionState.IN
                || (transactionState == TransactionState.COMMITTED
                && commitInProgress);
    }

    @Override
    public boolean notifyMessageSent() {
        if (hasActiveTransaction()) {
            boolean firstMessageInCurrentTransaction
                    = !messageSentInCurrentTransaction;
            messageSentInCurrentTransaction = true;
            return firstMessageInCurrentTransaction;
        } else {
            if (transactionState == TransactionState.COMMITTED
                    || transactionState == TransactionState.ABORTED) {
                cleanupTransaction(TransactionState.NONE);
            }
            return false;
        }
    }

    @Override
    public TransactionOptions getTransactionOptions() {
        isTrue("in transaction", transactionState == TransactionState.IN
                || transactionState == TransactionState.COMMITTED);
        return transactionOptions;
    }

    @Override
    public void startTransaction() {
        startTransaction(TransactionOptions.builder().build());
    }

    @Override
    public void startTransaction(final TransactionOptions transactionOptions) {
        notNull("transactionOptions", transactionOptions);
        if (transactionState == TransactionState.IN) {
            throw new IllegalStateException("Transaction already in progress");
        }
        if (transactionState == TransactionState.COMMITTED) {
            cleanupTransaction(TransactionState.IN);
        } else {
            transactionState = TransactionState.IN;
        }
        getServerSession().advanceTransactionNumber();
        this.transactionOptions = TransactionOptions.merge(
                transactionOptions,
                getOptions().getDefaultTransactionOptions());

        WriteConcern writeConcern = this.transactionOptions.getWriteConcern();
        if (writeConcern == null) {
            throw new MongoInternalException(
                    "Invariant violated. "
                    + "Transaction options write concern can not be null");
        }
        if (!writeConcern.isAcknowledged()) {
            throw new MongoClientException("Transactions do not support "
                    + "unacknowledged write concern");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void commitTransaction() {
        if (transactionState == TransactionState.ABORTED) {
            throw new IllegalStateException(
                    "Cannot call commitTransaction after calling abortTransaction");
        }
        if (transactionState == TransactionState.NONE) {
            throw new IllegalStateException("There is no transaction started");
        }
        try {
            if (messageSentInCurrentTransaction) {
                ReadConcern readConcern = transactionOptions.getReadConcern();
                if (readConcern == null) {
                    throw new MongoInternalException("Invariant violated."
                            + " Transaction options read concern can not be null");
                }
                commitInProgress = true;
                delegate.getOperationExecutor().execute(
                        new CommitTransactionOperation(
                                transactionOptions.getWriteConcern()),
                        readConcern, this);
            }
        } finally {
            commitInProgress = false;
            transactionState = TransactionState.COMMITTED;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void abortTransaction() {
        if (transactionState == TransactionState.ABORTED) {
            throw new IllegalStateException("Cannot call abortTransaction twice");
        }
        if (transactionState == TransactionState.COMMITTED) {
            throw new IllegalStateException(
                    "Cannot call abortTransaction after calling commitTransaction");
        }
        if (transactionState == TransactionState.NONE) {
            throw new IllegalStateException("There is no transaction started");
        }
        try {
            if (messageSentInCurrentTransaction) {
                ReadConcern readConcern = transactionOptions.getReadConcern();
                if (readConcern == null) {
                    throw new MongoInternalException("Invariant violated."
                            + " Transaction options read concern can not be null");
                }
                delegate.getOperationExecutor().execute(
                        new AbortTransactionOperation(
                                transactionOptions.getWriteConcern()),
                        readConcern, this);
            }
        } catch (Exception e) {
            // ignore errors
        } finally {
            cleanupTransaction(TransactionState.ABORTED);
        }
    }

    @Override
    public void close() {
        try {
            if (transactionState == TransactionState.IN) {
                abortTransaction();
            }
        } finally {
            // this release the session from the pool, 
            // not required in our implementation
            // super.close();
        }
    }

    private void cleanupTransaction(final TransactionState nextState) {
        messageSentInCurrentTransaction = false;
        transactionOptions = null;
        transactionState = nextState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSid());
    }

    public String getSid() {
        if (getServerSession() != null
                && getServerSession()
                        .getIdentifier() != null
                && getServerSession()
                        .getIdentifier().isBinary()) {
            return getServerSession()
                    .getIdentifier()
                    .asBinary()
                    .asUuid()
                    .toString();
        } else {
            return null;
        }

    }
}
