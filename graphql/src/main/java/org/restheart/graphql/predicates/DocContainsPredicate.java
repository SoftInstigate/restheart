/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
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

import org.restheart.utils.DocInExchange;

import com.google.common.collect.Sets;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;

/**
 * a predicate that resolve to true if the request contains the specified keys
 */
public class DocContainsPredicate implements Predicate, GQLPredicate {
    private final Set<String> fields;

    public DocContainsPredicate(String[] fields) {
        if (fields == null || fields.length < 1) {
            throw new IllegalArgumentException("doc-contains predicate must specify a list of fields");
        }

        this.fields = Sets.newHashSet(fields);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var doc = DocInExchange.doc(exchange);
        return doc != null && this.fields.stream().allMatch(doc.keySet()::contains);
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "doc-contains";
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
            return new DocContainsPredicate((String[]) config.get("fields"));
        }
    }
}



