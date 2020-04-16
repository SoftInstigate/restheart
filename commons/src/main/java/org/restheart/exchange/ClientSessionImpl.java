/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.exchange;

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
