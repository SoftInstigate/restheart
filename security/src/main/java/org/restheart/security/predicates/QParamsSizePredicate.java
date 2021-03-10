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



