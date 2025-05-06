/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;

/**
 * a predicate that resolve to true if ExchangeAttribute 'value' is in 'array'
 */
public class InPredicate implements Predicate {
    private final String[] array;
    private final ExchangeAttribute value;

    public InPredicate(String[] array, ExchangeAttribute value) {
        this.array = array;
        this.value = value;
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return Arrays.stream(array).filter(e -> e != null).anyMatch(this.value.readAttribute(exchange)::equals);
    }


    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "in";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            var params = Maps.<String, Class<?>>newHashMap();
            params.put("array", String[].class);
            params.put("value", ExchangeAttribute.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Sets.newHashSet("array", "value");
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new InPredicate((String[]) config.get("array"), (ExchangeAttribute) config.get("value"));
        }
    }
}
