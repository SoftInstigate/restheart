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
import com.mongodb.MongoClientException;
import com.mongodb.MongoInternalException;
import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.internal.MongoClientDelegate;
import com.mongodb.internal.session.ServerSessionPool;
import com.mongodb.operation.AbortTransactionOperation;
import com.mongodb.operation.CommitTransactionOperation;
import com.restheart.txns.db.Txn.TransactionStatus;
import static org.bson.assertions.Assertions.isTrue;
import static org.bson.assertions.Assertions.notNull;
import org.restheart.mongodb.db.sessions.ClientSessionImpl;
import org.restheart.mongodb.db.sessions.ServerSessionImpl;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class TxnClientSessionImpl extends ClientSessionImpl {
    private final MongoClientDelegate delegate;
    private TransactionStatus transactionState = TransactionStatus.NONE;
    private boolean commitInProgress;
    private TransactionOptions transactionOptions;
    private Txn txnServerStatus = null;

    public TxnClientSessionImpl(final ServerSessionPool serverSessionPool,
            final Object originator,
            final ClientSessionOptions options,
            final MongoClientDelegate delegate,
            final Txn txnServerStatus) {
        super(serverSessionPool, originator, options);
        this.delegate = delegate;
        this.txnServerStatus = txnServerStatus;
    }

    public void setMessageSentInCurrentTransaction(
            boolean messageSentInCurrentTransaction) {
        this.messageSentInCurrentTransaction = messageSentInCurrentTransaction;
    }

    public boolean isMessageSentInCurrentTransaction() {
        return this.messageSentInCurrentTransaction;
    }

    @Override
    public boolean hasActiveTransaction() {
        return transactionState == TransactionStatus.IN
                || (transactionState == TransactionStatus.COMMITTED
                && commitInProgress);
    }

    public boolean isTransacted() {
        return txnServerStatus != null;
    }

    @Override
    public boolean notifyMessageSent() {
        if (hasActiveTransaction()) {
            boolean firstMessageInCurrentTransaction
                    = !messageSentInCurrentTransaction;
            messageSentInCurrentTransaction = true;
            return firstMessageInCurrentTransaction;
        } else {
            if (transactionState == TransactionStatus.COMMITTED
                    || transactionState == TransactionStatus.ABORTED) {
                cleanupTransaction(TransactionStatus.NONE);
            }
            return false;
        }
    }

    @Override
    public TransactionOptions getTransactionOptions() {
        isTrue("in transaction", transactionState == TransactionStatus.IN
                || transactionState == TransactionStatus.COMMITTED);
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

    public void setServerSessionTransactionNumber(long number) {
        ((ServerSessionImpl) getServerSession()).setTransactionNumber(number);
    }

    public void setTransactionState(TransactionStatus transactionState) {
        startTransaction(); // this inits the transactionOptions
        this.transactionState = transactionState;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void commitTransaction() {
        if (transactionState == TransactionStatus.ABORTED) {
            throw new IllegalStateException(
                    "Cannot call commitTransaction after calling abortTransaction");
        }
        if (transactionState == TransactionStatus.NONE) {
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
            transactionState = TransactionStatus.COMMITTED;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void abortTransaction() {
        if (transactionState == TransactionStatus.ABORTED) {
            throw new IllegalStateException("Cannot call abortTransaction twice");
        }
        if (transactionState == TransactionStatus.COMMITTED) {
            throw new IllegalStateException(
                    "Cannot call abortTransaction after calling commitTransaction");
        }
        if (transactionState == TransactionStatus.NONE) {
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
