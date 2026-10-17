/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

/**
 * How several filters become one query. Written once and shared, because the two callers are
 * {@code ?filter} — which may repeat — and the combination of an ACL read filter with a caller's
 * own, where narrowing the query differently than intended is a security bug.
 */
public class BsonUtilsAndTest {

    private static final BsonDocument A = BsonDocument.parse("{ \"a\": 1 }");
    private static final BsonDocument B = BsonDocument.parse("{ \"b\": 2 }");

    @Test
    public void nothingToNarrowByIsAnEmptyQuery() {
        assertTrue(BsonUtils.and(List.of()).isEmpty());
        assertTrue(BsonUtils.and(null).isEmpty());
    }

    @Test
    public void oneFilterIsItself_notWrapped() {
        assertEquals(A, BsonUtils.and(List.of(A)));
    }

    @Test
    public void twoOrMoreBecomeAnAnd() {
        assertEquals(BsonDocument.parse("{ \"$and\": [ { \"a\": 1 }, { \"b\": 2 } ] }"), BsonUtils.and(List.of(A, B)));
    }

    @Test
    public void orderIsPreserved() {
        assertEquals(BsonDocument.parse("{ \"$and\": [ { \"b\": 2 }, { \"a\": 1 } ] }"), BsonUtils.and(List.of(B, A)));
    }

    @Test
    public void anEmptyFilterIsDropped_notWrapped() {
        // {} matches everything, so carrying it into an $and adds nothing but noise
        assertEquals(A, BsonUtils.and(List.of(new BsonDocument(), A)));
        assertEquals(A, BsonUtils.and(List.of(A, new BsonDocument())));
    }

    @Test
    public void nullEntriesAreIgnored() {
        // the ACL side is null whenever the permission declares no read filter
        assertEquals(A, BsonUtils.and(Arrays.asList(A, null)));
        assertEquals(A, BsonUtils.and(Arrays.asList(null, A)));
        assertTrue(BsonUtils.and(Arrays.asList(null, null)).isEmpty());
    }

    @Test
    public void theResultDoesNotAliasTheInput() {
        // callers add to what they get back — GetCollectionHandler does — and must not reach
        // through into the document they passed in
        var single = BsonUtils.and(List.of(A));
        single.put("c", A.get("a"));

        assertNotSame(A, single);
        assertEquals(BsonDocument.parse("{ \"a\": 1 }"), A);
    }
}
