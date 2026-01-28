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

import org.junit.jupiter.api.Test;

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
}
