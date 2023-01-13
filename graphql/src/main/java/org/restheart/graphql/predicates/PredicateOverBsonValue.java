package org.restheart.graphql.predicates;

import org.bson.BsonValue;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

public interface PredicateOverBsonValue extends Predicate {
    default boolean resolve(HttpServerExchange exchage) {
        return resolve(ExchangeWithBsonValue.value(exchage));
    }

   boolean resolve(BsonValue doc);
}
