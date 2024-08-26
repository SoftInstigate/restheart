/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2024 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */

package org.restheart.graphql.predicates;

import org.apache.commons.jxpath.JXPathContext;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.restheart.utils.BsonUtils.document;

import io.undertow.predicate.Predicates;

public class PredicatesTest {
    @Test
    public void testNullVsAbsent() {
        var doc = document().put("bar", 1).get();
        var ctx = JXPathContext.newContext(doc);
        var p = (PredicateOverJxPathCtx) Predicates.parse("field-exists(bar)");
        var np = (PredicateOverJxPathCtx) Predicates.parse("field-exists(foo)");

        assertTrue(p.resolve(ctx));
        assertFalse(np.resolve(ctx));

        var nestedDoc = document().put("bar", document().put("foo", 1)).get();
        var nestedDocCtx = JXPathContext.newContext(nestedDoc);

        var _p = (PredicateOverJxPathCtx) Predicates.parse("field-exists(bar.foo)");
        var _np = (PredicateOverJxPathCtx) Predicates.parse("field-exists(bar.not)");

        assertTrue(_p.resolve(nestedDocCtx));
        assertFalse(_np.resolve(nestedDocCtx));
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
