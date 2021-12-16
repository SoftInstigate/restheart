package org.restheart.security.predicates;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;

/**
 * a predicate that resolve to true if 'value' is in 'array'
 */
public class InPredicate implements Predicate {
    private final String[] array;
    private final ExchangeAttribute value;

    public InPredicate(String[] array, ExchangeAttribute value) {
        this.array = array;
        this.value = value;
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return Arrays.stream(array).filter(e -> e != null).anyMatch(this.value.readAttribute(exchange)::equals);
    }


    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "in";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            var params = Maps.<String, Class<?>>newHashMap();
            params.put("array", String[].class);
            params.put("value", ExchangeAttribute.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Sets.newHashSet("array", "value");
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new InPredicate((String[]) config.get("array"), (ExchangeAttribute) config.get("value"));
        }
    }
}
