/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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

import com.google.common.collect.Sets;

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
 * all keys in the request content are mot in the specified blacklist
 */
public class BsonRequestBlacklistPredicate implements Predicate {
    private static final Logger LOGGER = LoggerFactory.getLogger(BsonRequestBlacklistPredicate.class);
    private final Set<String> blacklist;

    public BsonRequestBlacklistPredicate(String[] blacklist) {
        if (blacklist == null || blacklist.length < 1) {
            throw new IllegalArgumentException("bson-request-blacklist predicate must specify a list of json properties");
        }

        this.blacklist = Sets.newHashSet(blacklist);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var _request = Request.of(exchange);

        if (_request == null || !(_request instanceof BsonRequest)) {
            LOGGER.warn("bson-request-blacklist predicate not invoked on BsonRequest but {}, it won't allow the request", _request == null ? _request: _request.getClass().getSimpleName());
            return false;
        }

        return !BsonUtils.containsKeys(((BsonRequest)_request).getContent(), this.blacklist, false);
    }


    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "bson-request-blacklist";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("blacklist", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("blacklist");
        }

        @Override
        public String defaultParameter() {
            return "blacklist";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new BsonRequestBlacklistPredicate((String[]) config.get("blacklist"));
        }
    }
}
