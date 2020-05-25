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
