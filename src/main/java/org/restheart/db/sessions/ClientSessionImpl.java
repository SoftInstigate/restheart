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
import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.TransactionBody;
import com.mongodb.client.internal.MongoClientDelegate;
import com.mongodb.internal.session.BaseClientSessionImpl;
import com.mongodb.internal.session.ServerSessionPool;
import java.util.Objects;
import java.util.UUID;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ClientSessionImpl
        extends BaseClientSessionImpl
        implements ClientSession {

    /**
     *
     */
    protected boolean messageSentInCurrentTransaction;
    private boolean causallyConsistent = true;

    /**
     *
     * @param serverSessionPool
     * @param originator
     * @param options
     * @param delegate
     */
    public ClientSessionImpl(final ServerSessionPool serverSessionPool,
            final Object originator,
            final ClientSessionOptions options,
            final MongoClientDelegate delegate) {
        super(serverSessionPool, originator, options);
    }

    /**
     *
     * @param serverSessionPool
     * @param originator
     * @param options
     */
    public ClientSessionImpl(final ServerSessionPool serverSessionPool,
            final Object originator,
            final ClientSessionOptions options) {
        super(serverSessionPool, originator, options);
    }
    
    /**
     *
     * @param <T>
     * @param tb
     * @return
     */
    @Override
    public <T> T withTransaction(TransactionBody<T> tb) {
        return tb.execute();
    }

    /**
     *
     * @param <T>
     * @param tb
     * @param to
     * @return
     */
    @Override
    public <T> T withTransaction(TransactionBody<T> tb, TransactionOptions to) {
        return tb.execute();
    }

    /**
     *
     * @return
     */
    @Override
    public boolean hasActiveTransaction() {
        return false;
    }

    /**
     *
     * @return
     */
    @Override
    public boolean isCausallyConsistent() {
        return causallyConsistent;
    }

    /**
     *
     * @return
     */
    @Override
    public boolean notifyMessageSent() {
        boolean firstMessageInCurrentTransaction
                = !messageSentInCurrentTransaction;
        messageSentInCurrentTransaction = true;
        return firstMessageInCurrentTransaction;
    }

    /**
     *
     * @return
     */
    @Override
    public TransactionOptions getTransactionOptions() {
        throw new UnsupportedOperationException();
    }

    /**
     *
     */
    @Override
    public void startTransaction() {
        throw new UnsupportedOperationException();
    }

    /**
     *
     * @param transactionOptions
     */
    @Override
    public void startTransaction(final TransactionOptions transactionOptions) {
        throw new UnsupportedOperationException();
    }

    /**
     *
     */
    @Override
    @SuppressWarnings("deprecation")
    public void commitTransaction() {
        throw new UnsupportedOperationException();
    }

    /**
     *
     */
    @Override
    @SuppressWarnings("deprecation")
    public void abortTransaction() {
        throw new UnsupportedOperationException();
    }

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
     *
     * @return
     */
    public UUID getSid() {
        return getSid(this);
    }
    
    /**
     *
     * @param causallyConsistent
     */
    public void setCausallyConsistent(boolean causallyConsistent) {
        this.causallyConsistent = causallyConsistent;
    }
    
    /**
     *
     * @param messageSentInCurrentTransaction
     */
    public void setMessageSentInCurrentTransaction(
            boolean messageSentInCurrentTransaction) {
        this.messageSentInCurrentTransaction = messageSentInCurrentTransaction;
    }

    /**
     *
     * @return
     */
    public boolean isMessageSentInCurrentTransaction() {
        return this.messageSentInCurrentTransaction;
    }

    /**
     *
     * @param cs
     * @return
     */
    public static UUID getSid(ClientSession cs) {
        if (cs != null
                && cs.getServerSession() != null
                && cs.getServerSession()
                        .getIdentifier() != null
                && cs.getServerSession()
                        .getIdentifier().isDocument()
                && cs.getServerSession()
                        .getIdentifier().asDocument().containsKey("id")
                && cs.getServerSession()
                        .getIdentifier().asDocument().get("id").isBinary()) {
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
}
