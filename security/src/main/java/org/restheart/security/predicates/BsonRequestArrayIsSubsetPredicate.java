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
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.Request;
import org.restheart.utils.BsonUtils;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a predicate that resolve to true if the request content is bson and
 * the property 'key' (can use the dot notation) is an array that is a subset of 'values'
 */
public class BsonRequestArrayIsSubsetPredicate implements Predicate {
    private static final Logger LOGGER = LoggerFactory.getLogger(BsonRequestArrayContainsPredicate.class);

    private final String key;
    private final BsonArray values;

    public BsonRequestArrayIsSubsetPredicate(String key, String[] values) {
        if (key == null || values == null) {
            throw new IllegalArgumentException("bson-request-array-is-subset predicate must specify key and array");
        }

        this.key = key;
        this.values = new BsonArray();

        for (var value: values) {
            try {
                this.values.add(BsonUtils.parse(value));
            } catch(Throwable t) {
                throw new IllegalArgumentException(value + " is not a valid bson value");
            }
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var _request = Request.of(exchange);

        if (_request == null) {
            LOGGER.warn("bson-request-array-is-subset predicate invkoed on null BsonRequest");
            return false;
        }

        if (_request instanceof BsonRequest bsonRequest) {
            if (bsonRequest.getContent() == null) {
                LOGGER.warn("bson-request-array-is-subset predicate invoked on a BsonRequest with null content");
                return false;
            }

            if (bsonRequest.getContent() instanceof BsonDocument contentDoc) {
                var _values = BsonUtils.get(contentDoc, this.key);

                if (_values.isPresent()) {
                    var values = _values.get();

                    if (values.isArray()) {
                        return this.values.containsAll(values.asArray());
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                LOGGER.warn("bson-request-array-is-subset predicate invoked on a BsonRequest with content {}, it requires a BsonDocument",  bsonRequest.getContent().getClass().getSimpleName());
                return false;
            }

        } else {
            LOGGER.warn("bson-request-array-is-subset predicate not invoked on a BsonRequest");
            return false;
        }
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "bson-request-array-is-subset";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            var params = Maps.<String, Class<?>>newHashMap();
            params.put("key", String.class);
            params.put("values", String[].class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Sets.newHashSet("key", "values");
        }

        @Override
        public String defaultParameter() {
            return "key";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new BsonRequestArrayIsSubsetPredicate((String) config.get("key"), (String[]) config.get("values"));
        }
    }
}
