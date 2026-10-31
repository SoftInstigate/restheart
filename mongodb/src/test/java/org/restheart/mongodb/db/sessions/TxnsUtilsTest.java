/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import com.mongodb.MongoQueryException;
import com.mongodb.ServerAddress;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class TxnsUtilsTest {
    @Test
    public void testNoWithTxnNumber() {
        var msg = "10";
        assertEquals(TxnsUtils.removeWithTxnNumber(msg), "10");
    }

    @Test
    public void testRemoveWithTxnNumber() {
        var msg = "with txnNumber 10";
        assertEquals(TxnsUtils.removeWithTxnNumber(msg), "10");
    }

    @Test
    public void testRemoveWithTxnNumber6() {
        var msg = "with { txnNumber: 10 }";
        assertEquals(TxnsUtils.removeWithTxnNumber(msg), "10");
    }

    @Test
    public void testRemoveWithtxnNumberAndRetryCounter() {
        var msg = "with txnNumberAndRetryCounter { txnNumber: 10, txnRetryCounter: 0 }";
        assertEquals(TxnsUtils.removeWithTxnNumber(msg), "10");
    }

    @Test
    public void testRemoveWithTxnNumberMongo8() {
        // MongoDB 8.0 format: number followed by "on session" info before "using txnRetryCounter"
        var msg = "1 on session fd3cb55b-f135-46ab-bdc7-2059230587ea - 47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU= -  -  using txnRetryCounter 0";
        assertEquals(TxnsUtils.removeWithTxnNumber(msg), "1");
    }

    // The messages below are the ones MongoDB 9.0.4 returns, captured from the server; 8.0 returns
    // the same text. They go through the whole parsing, not only removeWithTxnNumber.

    private static MongoQueryException error(int code, String codeName, String errmsg) {
        var response = new BsonDocument("ok", new BsonDouble(0))
                .append("errmsg", new BsonString(errmsg))
                .append("code", new BsonInt32(code))
                .append("codeName", new BsonString(codeName));
        return new MongoQueryException(response, new ServerAddress());
    }

    @Test
    public void testTransactionTooOldMongo9() {
        // the number wanted is the newer transaction's, not the one in the rejected request; both
        // are above the int range
        var e = error(225, "TransactionTooOld", "Cannot start transaction with { txnNumber: 9876543209 } on session "
                + "38c108ba-3546-40ba-81af-77f9d789cebf - 47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU= -  -  "
                + "because a newer transaction with txnNumberAndRetryCounter { txnNumber: 9876543210, txnRetryCounter: 0 } "
                + "has already started on this session.");
        assertEquals(9876543210L, TxnsUtils.getTxnNumFromExc(e));
    }

    @Test
    public void testNoSuchTransactionMongo9() {
        // the number wanted is the one the state refers to, the given one, not the active one
        var e = error(251, "NoSuchTransaction", "Given transaction number 9876543210 on session "
                + "b07a226f-8b5b-4ef4-938a-a3e1b0db0614 - 47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU= -  -  "
                + "using txnRetryCounter 0 does not match any in-progress transactions. "
                + "The active transaction number is -1");
        assertEquals(9876543210L, TxnsUtils.getTxnNumFromExc(e));
        assertEquals(Txn.TransactionStatus.NONE, TxnsUtils.getTxnStateFromExc(e));
    }

    @Test
    public void testTransactionAbortedMongo9() {
        var e = error(251, "NoSuchTransaction", "Transaction with { txnNumber: 2 } has been aborted.");
        assertEquals(2, TxnsUtils.getTxnNumFromExc(e));
        assertEquals(Txn.TransactionStatus.ABORTED, TxnsUtils.getTxnStateFromExc(e));
    }

    @Test
    public void testTransactionCommittedMongo9() {
        var e = error(256, "TransactionCommitted", "Transaction with { txnNumber: 1 } has been committed.");
        assertEquals(1, TxnsUtils.getTxnNumFromExc(e));
        assertEquals(Txn.TransactionStatus.COMMITTED, TxnsUtils.getTxnStateFromExc(e));
    }

    @Test
    public void testUnknownErrorIsRethrown() {
        var e = error(11000, "DuplicateKey", "E11000 duplicate key error");
        assertSame(e, assertThrows(MongoQueryException.class, () -> TxnsUtils.getTxnNumFromExc(e)));
        assertSame(e, assertThrows(MongoQueryException.class, () -> TxnsUtils.getTxnStateFromExc(e)));
    }
}
