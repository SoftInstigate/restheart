package org.restheart.graphql.predicates;

import org.bson.BsonDocument;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

public interface PredicateOverDocument extends Predicate {
    default boolean resolve(HttpServerExchange exchage) {
        return resolve(DocInExchange.doc(exchage));
    }

   boolean resolve(BsonDocument doc);
}
