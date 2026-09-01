/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.security.predicates;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;

/**
 * Covers {@code gte} / {@code lte}, used to compare a {@code @}-variable carrying a computed
 * number against a threshold (e.g. {@code gte(@subscription.seats.over_limit_days, 5)}).
 */
public class NumericComparisonPredicatesTest {

    private static boolean resolve(String predicate) {
        // io.undertow.server.HttpServerExchange is stubbed under src/test/java in this
        // module; both operands here are constants, so the exchange is never read.
        return PredicateParser.parse(predicate, NumericComparisonPredicatesTest.class.getClassLoader())
                .resolve(new HttpServerExchange());
    }

    @Test
    public void gteComparesNumerically() {
        assertTrue(resolve("gte(5, 3)"));
        assertTrue(resolve("gte(5, 5)"), "must be inclusive at the boundary");
        assertFalse(resolve("gte(3, 5)"));
    }

    @Test
    public void lteComparesNumerically() {
        assertTrue(resolve("lte(3, 5)"));
        assertTrue(resolve("lte(5, 5)"), "must be inclusive at the boundary");
        assertFalse(resolve("lte(5, 3)"));
    }

    @Test
    public void comparesAsNumbersNotAsStrings() {
        // lexicographically "10" < "9", numerically 10 > 9 — this is the whole point of
        // these predicates over `equals`, which compares its attributes as strings.
        assertTrue(resolve("gte(10, 9)"));
        assertFalse(resolve("lte(10, 9)"));
    }

    @Test
    public void handlesDecimalsAndNegatives() {
        assertTrue(resolve("gte(2.5, 2.4)"));
        assertFalse(resolve("gte(-1, 0)"));
        assertTrue(resolve("lte(-2, -1)"));
    }

    @Test
    public void unparseableOperandResolvesToFalse_neverTrue() {
        // A comparison that cannot be evaluated must deny, not grant — this includes an
        // unresolved @variable, which the interpolator replaces with an opaque token.
        assertFalse(resolve("gte(abc, 5)"));
        assertFalse(resolve("gte(5, abc)"));
        assertFalse(resolve("lte(abc, 5)"));
        assertFalse(resolve("lte(5, abc)"));
    }

    @Test
    public void acceptsThePositionalFormUsersWriteByAnalogyWithEquals() {
        // The documented form. It only parses because the builders expose the two operands
        // as a single default parameter, the way io.undertow.predicate.EqualsPredicate does.
        assertTrue(resolve("gte(5, 3)"));
        assertTrue(resolve("lte(3, 5)"));
    }

    @Test
    public void rejectsWrongArityAtParseTime() {
        // Fails at startup, when the ACL is parsed — not silently at request time.
        assertThrows(RuntimeException.class, () -> resolve("gte(5)"));
        assertThrows(RuntimeException.class, () -> resolve("gte(5, 4, 3)"));
    }
}
