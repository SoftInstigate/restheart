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

/**
 * a predicate that resolve to true if the request content is bson and
 * contains all specified keys
 */
public class BsonRequestContainsPredicate implements Predicate {
    private final Set<String> keys;

    public BsonRequestContainsPredicate(String[] qparams) {
        this.keys = qparams == null ? Sets.newHashSet() : Sets.newHashSet(qparams);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var _request = Request.of(exchange);

        if (!(_request instanceof BsonRequest)) {
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
