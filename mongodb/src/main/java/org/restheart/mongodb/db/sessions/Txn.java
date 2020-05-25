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

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Txn {
    public enum TransactionStatus {
        NONE, IN, COMMITTED, ABORTED
    }

    private final TransactionStatus status;
    private final long txnId;

    public Txn(final long txnId, final TransactionStatus status) {
        this.txnId = txnId;
        this.status = status;
    }

    @Override
    public String toString() {
        return "Txn(txnId=" + getTxnId() + ", status=" + getStatus() + ")";
    }

    /**
     * @return the status
     */
    public TransactionStatus getStatus() {
        return status;
    }

    /**
     * @return the txid
     */
    public long getTxnId() {
        return txnId;
    }
}
