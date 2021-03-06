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
 * contains all specified keys
 */
public class BsonRequestContainsPredicate implements Predicate {
    private static final Logger LOGGER = LoggerFactory.getLogger(BsonRequestContainsPredicate.class);

    private final Set<String> keys;

    public BsonRequestContainsPredicate(String[] keys) {
        if (keys == null || keys.length < 1) {
            throw new IllegalArgumentException("bson-request-contains predicate must specify a list of json properties");
        }

        this.keys = Sets.newHashSet(keys);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var _request = Request.of(exchange);

        if (_request == null || !(_request instanceof BsonRequest)) {
            LOGGER.warn("bson-request-contains predicate not invoked on BsonRequest but {}, it won't allow the request", _request == null ? _request: _request.getClass().getSimpleName());
            return false;
        }

        return BsonUtils.containsKeys(((BsonRequest)_request).getContent(), this.keys, true);
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "bson-request-contains";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("keys", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("keys");
        }

        @Override
        public String defaultParameter() {
            return "keys";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new BsonRequestContainsPredicate((String[]) config.get("keys"));
        }
    }
}
