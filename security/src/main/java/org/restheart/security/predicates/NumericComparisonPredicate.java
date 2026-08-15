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

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

/**
 * Base for numeric-comparison predicates ({@code gte}, {@code lte}). Every {@code @variable}
 * that {@link org.restheart.security.AclVarsInterpolator} resolves is substituted into the
 * predicate text as a quoted string literal, whether the resolved value is a BSON number, a
 * date, or a string — so both sides of the comparison are read as {@code String} via
 * {@link ExchangeAttribute#readAttribute} and parsed as numbers here, the same way
 * {@code io.undertow.predicate.EqualsPredicate} compares its attributes as strings regardless
 * of how they were declared.
 *
 * <p>A side that does not parse as a number — including a value that failed to resolve
 * (replaced by the interpolator with an unguessable token, see {@code VarResolver}'s failure
 * semantics) — makes the predicate resolve to {@code false}, never {@code true}: a comparison
 * that cannot be evaluated must not grant access.
 */
abstract class NumericComparisonPredicate implements Predicate {

    private final ExchangeAttribute left;
    private final ExchangeAttribute right;

    /**
     * @param attributes exactly two attributes — the left and right operands. Takes an array
     *                   rather than two parameters so the builders can expose them as a single
     *                   default parameter, which is what makes the positional form
     *                   {@code gte(a, b)} parse; this mirrors
     *                   {@code io.undertow.predicate.EqualsPredicate}, the predicate users
     *                   pattern-match this syntax from.
     * @throws IllegalArgumentException if not given exactly two attributes
     */
    protected NumericComparisonPredicate(ExchangeAttribute[] attributes) {
        if (attributes == null || attributes.length != 2) {
            throw new IllegalArgumentException(
                    "numeric comparison predicates require exactly two values, got "
                            + (attributes == null ? 0 : attributes.length));
        }
        this.left = attributes[0];
        this.right = attributes[1];
    }

    @Override
    public final boolean resolve(HttpServerExchange exchange) {
        var leftValue = parseNumber(left.readAttribute(exchange));
        var rightValue = parseNumber(right.readAttribute(exchange));
        if (leftValue == null || rightValue == null) {
            return false;
        }
        return compare(leftValue, rightValue);
    }

    /** @return {@code true} if the comparison this predicate represents holds */
    protected abstract boolean compare(double left, double right);

    private static Double parseNumber(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
