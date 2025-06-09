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
package org.restheart.graphql.predicates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bson.BsonValue;
import org.restheart.utils.BsonUtils;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;

/**
 * a predicate that resolve to true if the request contains the specified keys
 */
public class ValueEqPredicate implements PredicateOverBsonValue {
    private final BsonValue value;

    public ValueEqPredicate(String value) {
        if (value == null) {
            throw new IllegalArgumentException("velue-eq predicate must specify the value");
        }

        this.value = BsonUtils.parse(value);
    }

    @Override
    public boolean resolve(BsonValue value) {
        return this.value.equals(value);
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "value-eq";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            var params = new HashMap<String, Class<?>>();
            params.put("value", String.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            var params = new HashSet<String>();
            params.add("value");
            return params;
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new ValueEqPredicate((String) config.get("value"));
        }
    }
}
