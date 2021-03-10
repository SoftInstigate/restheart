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
 * all keys in the request content are mot in the specified blacklist
 */
public class BsonRequestBlacklistPredicate implements Predicate {
    private final Set<String> whitelist;

    public BsonRequestBlacklistPredicate(String[] whitelist) {
        this.whitelist = whitelist == null ? Sets.newHashSet() : Sets.newHashSet(whitelist);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var _request = Request.of(exchange);

        if (!(_request instanceof BsonRequest)) {
            return false;
        }

        return !BsonUtils.containsKeys(((BsonRequest)_request).getContent(), this.whitelist, false);
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
