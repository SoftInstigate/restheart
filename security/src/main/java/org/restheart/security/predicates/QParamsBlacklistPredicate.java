package org.restheart.security.predicates;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;

/**
 * a predicate that resolve to true if none of query parameters in the request are
 * in the specified blacklist
 */
public class QParamsBlacklistPredicate implements Predicate {
    private final Set<String> blacklist;

    public QParamsBlacklistPredicate(String[] blacklist) {
        this.blacklist = blacklist == null ? Sets.newHashSet() : Sets.newHashSet(blacklist);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var qparamsInExchange = exchange.getQueryParameters();

        return qparamsInExchange == null
                || qparamsInExchange.keySet().stream().noneMatch(this.blacklist::contains);
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "qparams-blacklist";
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
            return new QParamsBlacklistPredicate((String[]) config.get("blacklist"));
        }
    }
}
