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

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;

/**
 * {@code gte(value1, value2)} — true if {@code value1 >= value2}, numerically.
 *
 * <p>Added so a {@code @}-variable exposing a computed number (e.g. a plugin's
 * {@code VarResolver} exposing days elapsed since some event) can be compared against a
 * threshold directly in an ACL predicate, without the module that computed the number also
 * having to decide what to do about it:
 *
 * <pre>{@code
 * predicate: "gte(@subscription.seats.over_limit_days, 5)"
 * }</pre>
 *
 * @see NumericComparisonPredicate
 * @see LtePredicate
 */
public class GtePredicate extends NumericComparisonPredicate {

    public GtePredicate(ExchangeAttribute[] values) {
        super(values);
    }

    @Override
    protected boolean compare(double left, double right) {
        return left >= right;
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "gte";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            var params = Maps.<String, Class<?>>newHashMap();
            params.put("value", ExchangeAttribute[].class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Sets.newHashSet("value");
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new GtePredicate((ExchangeAttribute[]) config.get("value"));
        }
    }
}
