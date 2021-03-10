package org.restheart.security.predicates;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;

/**
 * a predicate that resolve to true if the request contains all specified query parameters
 */
public class QParamsContainPredicate implements Predicate {
    private final Set<String> qparams;

    public QParamsContainPredicate(String[] qparams) {
        this.qparams = qparams == null ? Sets.newHashSet() : Sets.newHashSet(qparams);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var qparamsInExchange = exchange.getQueryParameters();

        return (qparamsInExchange == null && qparams.size() == 0)
            || (qparamsInExchange != null &&
                this.qparams.stream().allMatch(qparamsInExchange.keySet()::contains));
    }

    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "qparams-contain";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("qparams", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("qparams");
        }

        @Override
        public String defaultParameter() {
            return "qparams";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new QParamsContainPredicate((String[]) config.get("qparams"));
        }
    }
}



