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
 * {@code lte(value1, value2)} — true if {@code value1 <= value2}, numerically.
 *
 * @see NumericComparisonPredicate
 * @see GtePredicate
 */
public class LtePredicate extends NumericComparisonPredicate {

    public LtePredicate(ExchangeAttribute value1, ExchangeAttribute value2) {
        super(value1, value2);
    }

    @Override
    protected boolean compare(double left, double right) {
        return left <= right;
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "lte";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            var params = Maps.<String, Class<?>>newHashMap();
            params.put("value1", ExchangeAttribute.class);
            params.put("value2", ExchangeAttribute.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Sets.newHashSet("value1", "value2");
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new LtePredicate((ExchangeAttribute) config.get("value1"), (ExchangeAttribute) config.get("value2"));
        }
    }
}
