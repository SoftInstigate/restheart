/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.db.sessions;

import com.mongodb.ClientSessionOptions;
import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.TransactionBody;
import com.mongodb.internal.session.BaseClientSessionImpl;
import com.mongodb.internal.session.ServerSessionPool;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of MongoDB ClientSession for RESTHeart.
 * <p>
 * This class provides a custom implementation of the MongoDB {@link ClientSession} interface,
 * extending {@link BaseClientSessionImpl} to support session management in RESTHeart's
 * MongoDB operations. It handles session lifecycle, transaction support, and causal consistency
 * for database operations.
 * </p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Session pooling and management through {@link ServerSessionPool}</li>
 *   <li>Configurable causal consistency for read operations</li>
 *   <li>Transaction state tracking</li>
 *   <li>Custom session ID management</li>
 * </ul>
 * 
 * <p>Note: This implementation provides limited transaction support. The transaction
 * methods ({@link #startTransaction()}, {@link #commitTransaction()}, {@link #abortTransaction()})
 * throw {@link UnsupportedOperationException} as transactions are handled differently
 * in RESTHeart's architecture.</p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see ClientSession
 * @see BaseClientSessionImpl
 * @see ServerSessionPool
 */
public class ClientSessionImpl extends BaseClientSessionImpl implements ClientSession {

    /**
     * Flag indicating whether a message has been sent in the current transaction.
     * Used to track transaction state and ensure proper transaction semantics.
     */
    protected boolean messageSentInCurrentTransaction;
    
    /**
     * Flag indicating whether this session should enforce causal consistency.
     * When true, read operations will see the results of preceding write operations.
     * Defaults to true for consistency guarantees.
     */
    private boolean causallyConsistent = true;

    /**
     * Constructs a new ClientSessionImpl with the specified parameters.
     * 
     * @param serverSessionPool the pool from which to acquire server sessions.
     *                          Manages the lifecycle and reuse of server-side sessions
     * @param originator the object that created this session, typically a MongoClient instance.
     *                   Used to ensure operations are executed by the same client that created the session
     * @param options the client session options including causal consistency settings,
     *                default transaction options, and other session configuration
     */
    public ClientSessionImpl(final ServerSessionPool serverSessionPool, final Object originator, final ClientSessionOptions options) {
        super(serverSessionPool, originator, options);
    }

    /**
     * Executes the given transaction body.
     * <p>
     * Note: This implementation does not provide full transaction support.
     * It simply executes the transaction body without actual transaction semantics.
     * This is intentional as RESTHeart handles transactions at a different layer.
     * </p>
     * 
     * @param <T> the return type of the transaction body
     * @param tb the transaction body to execute
     * @return the result of executing the transaction body
     */
    @Override
    public <T> T withTransaction(TransactionBody<T> tb) {
        return tb.execute();
    }

    /**
     * Executes the given transaction body with specified transaction options.
     * <p>
     * Note: This implementation ignores the transaction options and does not provide
     * full transaction support. It simply executes the transaction body.
     * This is intentional as RESTHeart handles transactions at a different layer.
     * </p>
     * 
     * @param <T> the return type of the transaction body
     * @param tb the transaction body to execute
     * @param to the transaction options (ignored in this implementation)
     * @return the result of executing the transaction body
     */
    @Override
    public <T> T withTransaction(TransactionBody<T> tb, TransactionOptions to) {
        return tb.execute();
    }

    /**
     * Checks if this session has an active transaction.
     * 
     * @return always returns {@code false} as this implementation does not support
     *         traditional MongoDB transactions
     */
    @Override
    public boolean hasActiveTransaction() {
        return false;
    }

    /**
     * Checks if this session is configured for causal consistency.
     * <p>
     * Causal consistency ensures that read operations reflect the results of preceding
     * write operations. This provides a global partial order of operations that preserves
     * causality.
     * </p>
     * 
     * @return {@code true} if this session enforces causal consistency, {@code false} otherwise
     */
    @Override
    public boolean isCausallyConsistent() {
        return causallyConsistent;
    }

    /**
     * Notifies the session that a message has been sent.
     * <p>
     * This method is called by the MongoDB driver to track whether this is the first
     * message sent in the current transaction. This information is used to properly
     * manage transaction state.
     * </p>
     * 
     * @return {@code true} if this was the first message in the current transaction,
     *         {@code false} if a message has already been sent
     */
    @Override
    public boolean notifyMessageSent() {
        boolean firstMessageInCurrentTransaction = !messageSentInCurrentTransaction;
        messageSentInCurrentTransaction = true;
        return firstMessageInCurrentTransaction;
    }

    /**
     * Gets the transaction options for this session.
     * 
     * @return never returns as this implementation does not support transactions
     * @throws UnsupportedOperationException always thrown as transactions are not supported
     */
    @Override
    public TransactionOptions getTransactionOptions() {
        throw new UnsupportedOperationException();
    }

    /**
     * Starts a new transaction on this session.
     * 
     * @throws UnsupportedOperationException always thrown as this implementation
     *         does not support traditional MongoDB transactions
     */
    @Override
    public void startTransaction() {
        throw new UnsupportedOperationException();
    }

    /**
     * Starts a new transaction on this session with the given options.
     * 
     * @param transactionOptions the options to apply to the transaction (ignored)
     * @throws UnsupportedOperationException always thrown as this implementation
     *         does not support traditional MongoDB transactions
     */
    @Override
    public void startTransaction(final TransactionOptions transactionOptions) {
        throw new UnsupportedOperationException();
    }

    /**
     * Commits the current transaction.
     * 
     * @throws UnsupportedOperationException always thrown as this implementation
     *         does not support traditional MongoDB transactions
     */
    @Override
    public void commitTransaction() {
        throw new UnsupportedOperationException();
    }

    /**
     * Aborts the current transaction.
     * 
     * @throws UnsupportedOperationException always thrown as this implementation
     *         does not support traditional MongoDB transactions
     */
    @Override
    public void abortTransaction() {
        throw new UnsupportedOperationException();
    }

    /**
     * Closes this client session.
     * <p>
     * Note: This implementation intentionally does not call {@code super.close()}
     * which would release the session back to the pool. Session lifecycle is
     * managed differently in RESTHeart's architecture.
     * </p>
     */
    @Override
    public void close() {
        // this release the session from the pool,
        // not required in our implementation
        // super.close();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSid());
    }

    /**
     * Gets the session identifier (UUID) for this client session.
     * <p>
     * The session ID is extracted from the server session's BSON document
     * identifier. This ID uniquely identifies the session across the cluster.
     * </p>
     * 
     * @return the session UUID, or null if the session ID cannot be extracted
     */
    public UUID getSid() {
        return getSid(this);
    }

    /**
     * Sets whether this session should enforce causal consistency.
     * <p>
     * When enabled, read operations will reflect the results of preceding write
     * operations, providing a causally consistent view of the data.
     * </p>
     * 
     * @param causallyConsistent {@code true} to enable causal consistency,
     *                           {@code false} to disable it
     */
    public void setCausallyConsistent(boolean causallyConsistent) {
        this.causallyConsistent = causallyConsistent;
    }

    /**
     * Sets whether a message has been sent in the current transaction.
     * <p>
     * This method is used internally to track transaction state and ensure
     * proper transaction semantics.
     * </p>
     * 
     * @param messageSentInCurrentTransaction {@code true} if a message has been sent,
     *                                        {@code false} otherwise
     */
    public void setMessageSentInCurrentTransaction(boolean messageSentInCurrentTransaction) {
        this.messageSentInCurrentTransaction = messageSentInCurrentTransaction;
    }

    /**
     * Checks if a message has been sent in the current transaction.
     * 
     * @return {@code true} if a message has been sent in the current transaction,
     *         {@code false} otherwise
     */
    public boolean isMessageSentInCurrentTransaction() {
        return this.messageSentInCurrentTransaction;
    }

    /**
     * Extracts the session identifier (UUID) from a ClientSession.
     * <p>
     * This static utility method safely extracts the session ID from the server
     * session's BSON document. It handles null checks and type verification to
     * ensure safe extraction.
     * </p>
     * 
     * @param cs the client session from which to extract the ID
     * @return the session UUID, or null if the session is null or the ID cannot be extracted
     */
    public static UUID getSid(ClientSession cs) {
        if (cs != null
                && cs.getServerSession() != null
                && cs.getServerSession().getIdentifier() != null
                && cs.getServerSession().getIdentifier().isDocument()
                && cs.getServerSession().getIdentifier().asDocument().containsKey("id")
                && cs.getServerSession().getIdentifier().asDocument().get("id").isBinary()) {
            return cs.getServerSession()
                    .getIdentifier()
                    .asDocument()
                    .get("id")
                    .asBinary()
                    .asUuid();
        } else {
            return null;
        }
    }

    /**
     * Notifies the session that an operation has been initiated.
     * <p>
     * This method is called by the MongoDB driver before executing operations.
     * This implementation does not perform any action as operation tracking
     * is handled elsewhere in RESTHeart.
     * </p>
     * 
     * @param operation the operation being initiated (ignored in this implementation)
     */
    @Override
    public void notifyOperationInitiated(Object operation) {
        // nothing to do
    }

    /**
     * Extracts the delegate field from a MongoClient for session originator validation.
     * <p>
     * In mongo-java-legacy driver v4.3.2, MongoDelegate checks that operations are
     * executed by the same MongoClient that created the session by comparing the
     * originator to the value of MongoClient.delegate. This utility method uses
     * reflection to extract the delegate field value.
     * </p>
     * 
     * @param o the object (typically a MongoClient) from which to extract the delegate
     * @return the value of the delegate field if it exists and is accessible,
     *         otherwise returns the original object
     */
    @SuppressWarnings("unused")
    private static Object delegate(Object o) {
        try {
            var delegateF = o.getClass().getDeclaredField("delegate");
            delegateF.setAccessible(true);
            return delegateF.get(o);
        } catch(Throwable t) {
            return o;
        }
    }
}
