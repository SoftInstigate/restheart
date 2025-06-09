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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;

/**
 * a predicate that resolve to true if the request contains a number of 'size' query parameters
 */
public class QParamsSizePredicate implements Predicate {
    private final int size;

    public QParamsSizePredicate(int size) {
        this.size = size;
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var qparamsInExchange = exchange.getQueryParameters();

        return (qparamsInExchange == null && this.size == 0) ||
            (qparamsInExchange != null && qparamsInExchange.keySet().size() == this.size);
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "qparams-size";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("size", Integer.class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("size");
        }

        @Override
        public String defaultParameter() {
            return "size";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new QParamsSizePredicate((Integer) config.get("size"));
        }
    }
}
