package org.restheart.graphql.predicates;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.restheart.utils.BsonUtils.document;

import org.junit.jupiter.api.Test;

import io.undertow.predicate.Predicates;

public class PredicatesTest {

    @Test
    public void testNullVsAbsent() {
        var doc = document().put("bar", 1).get();
        var p = (PredicateOverBsonValue) Predicates.parse("field-exists(bar)");
        var np = (PredicateOverBsonValue) Predicates.parse("field-exists(foo)");

        assertTrue(p.resolve(doc));
        assertFalse(np.resolve(doc));

        var nestedDoc = document().put("bar", document().put("foo", 1)).get();

        var _p = (PredicateOverBsonValue) Predicates.parse("field-exists(bar.foo)");
        var _np = (PredicateOverBsonValue) Predicates.parse("field-exists(bar.not)");

        assertTrue(_p.resolve(nestedDoc));
        assertFalse(_np.resolve(nestedDoc));
    }

    @Test
    public void testDocContains() {
        var fooOrBar = "field-exists(sub.foo) or field-exists(bar)";
        var fooAndBar = "field-exists(sub.foo, bar)";

        var fooDoc = document().put("sub", document().put("foo", 1)).get();
        var barDoc = document().put("bar", 1).get();
        var fooAndBarDoc = document()
                .put("bar", 1)
                .put("sub", document().put("foo", 1)).get();

        var _fooOrBar = Predicates.parse(fooOrBar);
        var _fooAndBar = Predicates.parse(fooAndBar);

        assertTrue(_fooOrBar.resolve(ExchangeWithBsonValue.exchange(fooDoc)));
        assertTrue(_fooOrBar.resolve(ExchangeWithBsonValue.exchange(barDoc)));

        assertFalse(_fooAndBar.resolve(ExchangeWithBsonValue.exchange(fooDoc)));
        assertFalse(_fooAndBar.resolve(ExchangeWithBsonValue.exchange(barDoc)));
        assertTrue(_fooAndBar.resolve(ExchangeWithBsonValue.exchange(fooAndBarDoc)));
    }

    @Test
    public void testDocFieldEq() {
        var fooEqOne = "field-eq(field=sub.foo, value=1)";
        // string equality requires value='"a string"' or value="'a string'"
        var fooEqString = "field-eq(field=sub.string, value='\"a string\"')";
        var barEqObj = "field-eq(field=bar, value='{\"a\":1}')";

        var fooDoc = document().put("sub", document()
                .put("foo", 1)
                .put("string", "a string")).get();

        var barDoc = document().put("bar", document().put("a", 1)).get();

        var _fooEqOne = Predicates.parse(fooEqOne);
        var _barEqObj = Predicates.parse(barEqObj);
        var _fooEqString = Predicates.parse(fooEqString);

        assertTrue(_barEqObj.resolve(ExchangeWithBsonValue.exchange(barDoc)));
        assertTrue(_fooEqOne.resolve(ExchangeWithBsonValue.exchange(fooDoc)));
        assertTrue(_fooEqString.resolve(ExchangeWithBsonValue.exchange(fooDoc)));

        assertFalse(_barEqObj.resolve(ExchangeWithBsonValue.exchange(fooDoc)));
        assertFalse(_fooEqOne.resolve(ExchangeWithBsonValue.exchange(barDoc)));
    }
}
