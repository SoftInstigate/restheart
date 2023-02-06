/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2023 SoftInstigate
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.utils.BsonUtils;

import com.google.common.collect.Sets;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;

/**
 * a predicate that resolve to true if the request contains the specified keys
 */
public class FieldExists implements PredicateOverBsonValue {
    private final Set<String> fields;

    public FieldExists(String[] fields) {
        if (fields == null || fields.length < 1) {
            throw new IllegalArgumentException("field-exists predicate must specify a list of fields");
        }

        this.fields = Sets.newHashSet(fields);
    }

    @Override
    public boolean resolve(BsonValue value) {
        if (value instanceof BsonDocument doc) {
            return this.fields.stream().allMatch(f -> BsonUtils.get(doc, f).isPresent());
        } else {
            return false;
        }
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "field-exists";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("fields", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("fields");
        }

        @Override
        public String defaultParameter() {
            return "fields";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new FieldExists((String[]) config.get("fields"));
        }
    }
}



