package org.restheart.security.predicates;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;

/**
 * a predicate that resolve to true if all query parameters in the request are
 * in the specified whitelist
 */
public class QParamsWhitelistPredicate implements Predicate {
    private final Set<String> whitelist;

    public QParamsWhitelistPredicate(String[] whitelist) {
        this.whitelist = whitelist == null ? Sets.newHashSet() : Sets.newHashSet(whitelist);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var qparamsInExchange = exchange.getQueryParameters();

        if (whitelist.isEmpty()) {
            return qparamsInExchange.isEmpty();
        } else {
            return qparamsInExchange == null
                || qparamsInExchange.keySet().stream().allMatch(this.whitelist::contains);
        }
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "qparams-whitelist";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("whitelist", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("whitelist");
        }

        @Override
        public String defaultParameter() {
            return "whitelist";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new QParamsWhitelistPredicate((String[]) config.get("whitelist"));
        }
    }
}
