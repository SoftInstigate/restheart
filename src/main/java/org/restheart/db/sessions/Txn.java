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

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Txn {
    public enum TransactionState {
        NONE, IN, COMMITTED, ABORTED
    }

    private final TransactionState state;
    private final long txnId;

    public Txn(final long txnId, final TransactionState state) {
        this.txnId = txnId;
        this.state = state;
    }

    @Override
    public String toString() {
        if (txnId == -1 && state == TransactionState.NONE) {
            return "Txn(state=NOT_SUPPORTED)";
        } else {
            return "Txn(txnId=" + getTxnId() + ", state=" + getState() + ")";
        }
    }

    /**
     * @return the state
     */
    public TransactionState getState() {
        return state;
    }

    /**
     * @return the txid
     */
    public long getTxnId() {
        return txnId;
    }
    
    public boolean supportsTxns() {
        return txnId == -1 && state == TransactionState.NONE;
    }
    
    public static Txn newNotSupportingTxn() {
        return  new Txn(-1, TransactionState.NONE);
    }
}
