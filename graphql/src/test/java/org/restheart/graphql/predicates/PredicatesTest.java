package org.restheart.graphql.predicates;

import org.junit.Test;
import org.restheart.graphql.GraphQLService;
import org.restheart.utils.DocInExchange;

import io.undertow.predicate.Predicates;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.restheart.utils.BsonUtils.document;

public class PredicatesTest {
    @Test
    public void testExistsUndertow() {
        var predicate = "x-exists(foo) or x-exists(bar)";
        var t = document().put("foo", 1).get();
        var f = document().put("bar", 1).get();

        var _predicate = Predicates.parse(predicate);

        assertTrue(null, _predicate.resolve(DocInExchange.exchange(t)));
        assertTrue(null, _predicate.resolve(DocInExchange.exchange(f)));
    }
}




