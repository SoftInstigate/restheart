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
    public void testDocContains() {
        var fooOrBar = "doc-contains(foo) or doc-contains(bar)";
        var fooAndBar = "doc-contains(foo, bar)";

        var fooDoc = document().put("foo", 1).get();
        var barDoc = document().put("bar", 1).get();
        var fooAndBarDoc = document()
            .put("bar", 1)
            .put("foo", 1).get();

        var _fooOrBar = Predicates.parse(fooOrBar);
        var _fooAndBar = Predicates.parse(fooAndBar);

        assertTrue(_fooOrBar.resolve(DocInExchange.exchange(fooDoc)));
        assertTrue(_fooOrBar.resolve(DocInExchange.exchange(barDoc)));

        assertFalse(_fooAndBar.resolve(DocInExchange.exchange(fooDoc)));
        assertFalse(_fooAndBar.resolve(DocInExchange.exchange(barDoc)));
        assertTrue(_fooAndBar.resolve(DocInExchange.exchange(fooAndBarDoc)));
    }

    @Test
    public void testDocFieldEq() {
        var fooEqOne = "doc-field-eq(field=foo, value=1)";
        var barEqObj = "doc-field-eq(field=bar, value='{\"a\":1}')";

        var fooDoc = document().put("foo", 1).get();
        var barDoc = document().put("bar", document().put("a", 1)).get();

        var _fooEqOne = Predicates.parse(fooEqOne);
        var _barEqObj = Predicates.parse(barEqObj);

        assertTrue(_barEqObj.resolve(DocInExchange.exchange(barDoc)));
        assertTrue(_fooEqOne.resolve(DocInExchange.exchange(fooDoc)));

        assertFalse(_barEqObj.resolve(DocInExchange.exchange(fooDoc)));
        assertFalse(_fooEqOne.resolve(DocInExchange.exchange(barDoc)));
    }
}
