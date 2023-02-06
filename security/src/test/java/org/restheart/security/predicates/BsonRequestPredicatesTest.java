/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2023 SoftInstigate
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
package org.restheart.security.predicates;

import org.junit.Assert;
import org.junit.Test;
import org.restheart.security.utils.MongoUtils;
import org.restheart.utils.BsonUtils;

import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import static org.restheart.exchange.ExchangeWithRequestFactory.withBson;

public class BsonRequestPredicatesTest {
    @Test
    public void testJsonRequestContainsBson() {
        var predicateFooArray = PredicateParser.parse("bson-request-contains(foo, array)", MongoUtils.class.getClassLoader());
        var predicateFooArrayNotExisting = PredicateParser.parse("bson-request-contains(foo, array, notex)", MongoUtils.class.getClassLoader());
        var predicateFooBar = PredicateParser.parse("bson-request-contains(foo.bar)", MongoUtils.class.getClassLoader());
        var predicateArray = PredicateParser.parse("bson-request-contains(array)", MongoUtils.class.getClassLoader());
        var predicateArrayA = PredicateParser.parse("bson-request-contains(array.a)", MongoUtils.class.getClassLoader());
        var predicateArrayB = PredicateParser.parse("bson-request-contains(array.b)", MongoUtils.class.getClassLoader());
        var predicateBar = PredicateParser.parse("bson-request-contains(bar)", MongoUtils.class.getClassLoader());
        var predicateBarFoo = PredicateParser.parse("bson-request-contains(bar.foo)", MongoUtils.class.getClassLoader());
        var predicateUpdateOperators = PredicateParser.parse("bson-request-contains(p.q)", MongoUtils.class.getClassLoader());

        var exchange = exchangeWithBsonContent("{ 'foo':{'bar':true}, 'array': [{'a':1,'b':2},{'b':1}], '$set': {'p.q.r': true} }");

        Assert.assertTrue("check positive predicate foo,array", predicateFooArray.resolve(exchange));
        Assert.assertTrue("check positive predicate foo.bar", predicateFooBar.resolve(exchange));
        Assert.assertTrue("check positive predicate array", predicateArray.resolve(exchange));
        Assert.assertTrue("check positive predicate array.b", predicateArrayB.resolve(exchange));

        Assert.assertTrue("check positive predicate update operators", predicateUpdateOperators.resolve(exchange));

        Assert.assertFalse("check negative predicate array.a", predicateArrayA.resolve(exchange));
        Assert.assertFalse("check negative predicate foo,array,notex", predicateFooArrayNotExisting.resolve(exchange));
        Assert.assertFalse("check negative predicate", predicateBar.resolve(exchange));
        Assert.assertFalse("check negative predicate", predicateBarFoo.resolve(exchange));
    }

    @Test
    public void testJsonRequestWhitelistBson() {
        var predicate = PredicateParser.parse("bson-request-whitelist(a, c, us.d, d.n)", MongoUtils.class.getClassLoader());
        var predicateEmpty = PredicateParser.parse("bson-request-whitelist()", MongoUtils.class.getClassLoader());

        var exchangeOK1 = exchangeWithBsonContent("{ 'a': {'b': 1}, 'c': true, '$unset': {'us.d': true} }");
        var exchangeOK2 = exchangeWithBsonContent("{ 'd.n': true }");
        var exchangeOK3 = exchangeWithBsonContent("{ 'd.n.foo': true }");
        var exchangeEmpty = exchangeWithBsonContent("{}");

        Assert.assertTrue("check positive predicate", predicate.resolve(exchangeOK1));
        Assert.assertTrue("check positive predicate", predicate.resolve(exchangeOK2));
        Assert.assertTrue("check positive predicate", predicate.resolve(exchangeOK3));

        var exchangeKO1 = exchangeWithBsonContent("{ 'other': {'b': 1} }");
        var exchangeKO2 = exchangeWithBsonContent("{ 'us': {'b': 1} }");
        var exchangeKO3 = exchangeWithBsonContent("{ 'us': true }");
        var exchangeKO4 = exchangeWithBsonContent("{ 'd': true }");

        Assert.assertFalse("check negative predicate 1", predicate.resolve(exchangeKO1));
        Assert.assertFalse("check negative predicate 2", predicate.resolve(exchangeKO2));
        Assert.assertFalse("check negative predicate 2", predicate.resolve(exchangeKO3));
        Assert.assertFalse("check negative predicate 2", predicate.resolve(exchangeKO4));

        Assert.assertFalse("check negative predicate 2", predicateEmpty.resolve(exchangeKO1));
        Assert.assertTrue("check negative predicate 2", predicateEmpty.resolve(exchangeEmpty));


    }

    @Test
    public void testJsonRequestBlacklistBson() {
        var predicateFooArray = PredicateParser.parse("bson-request-blacklist(foo, array)", MongoUtils.class.getClassLoader());

        var exchangeFooArrayOK = exchangeWithBsonContent("{ 'good': true }");

        Assert.assertTrue("check positive predicate FooArray", predicateFooArray.resolve(exchangeFooArrayOK));

        var exchangeFooArrayKO1 = exchangeWithBsonContent("{ 'foo': {'bar':true} }");
        var exchangeFooFooArrayKO2 = exchangeWithBsonContent("{ 'foo.bar': true }");
        var exchangeFooFooArrayKO3 = exchangeWithBsonContent("{ 'foo': null }");

        Assert.assertFalse("check negative predicate FooFooArrayKO1", predicateFooArray.resolve(exchangeFooArrayKO1));
        Assert.assertFalse("check negative predicate FooFooArrayKO2", predicateFooArray.resolve(exchangeFooFooArrayKO2));
        Assert.assertFalse("check negative predicate FooFooArrayKO3", predicateFooArray.resolve(exchangeFooFooArrayKO3));

        var predicateFooDotBar = PredicateParser.parse("bson-request-blacklist(foo.bar)", MongoUtils.class.getClassLoader());

        var exchangeFooDotBarOK1 = exchangeWithBsonContent("{ 'foo': true }");
        var exchangeFooDotBarOK2 = exchangeWithBsonContent("{ 'foo': { 'other': true } }");

        Assert.assertTrue("check positive predicate good", predicateFooDotBar.resolve(exchangeFooDotBarOK1));
        Assert.assertTrue("check positive predicate good", predicateFooDotBar.resolve(exchangeFooDotBarOK2));

        var exchangeFooDotBarKO1 = exchangeWithBsonContent("{ 'foo.bar': true }");
        var exchangeFooDotBarKO2 = exchangeWithBsonContent("{ 'foo': { 'bar': { 'more': true}} }");
        var exchangeFooDotBarKO3 = exchangeWithBsonContent("{ 'foo.bar.more': true }");

        Assert.assertFalse("check negative predicate FooDotBarKO1", predicateFooDotBar.resolve(exchangeFooDotBarKO1));
        Assert.assertFalse("check negative predicate FooDotBarKO2", predicateFooDotBar.resolve(exchangeFooDotBarKO2));
        Assert.assertFalse("check negative predicate FooDotBarKO3", predicateFooDotBar.resolve(exchangeFooDotBarKO3));

        var predicateArrayArray2 = PredicateParser.parse("bson-request-blacklist(array, array2.foo)", MongoUtils.class.getClassLoader());

        var exchangeArrayArray2OK1 = exchangeWithBsonContent("{ 'array2': [] }");
        var exchangeArrayArray2OK2 = exchangeWithBsonContent("{ 'array2': [ {'bar': true}, {'bar': false} ] }");
        var exchangeArrayArray2OK3 = exchangeWithBsonContent("{ 'array2': [ 1, 2, 3 ] }");

        Assert.assertTrue("check positive predicate ArrayArray2OK1", predicateArrayArray2.resolve(exchangeArrayArray2OK1));
        Assert.assertTrue("check positive predicate ArrayArray2OK2", predicateArrayArray2.resolve(exchangeArrayArray2OK2));
        Assert.assertTrue("check positive predicate ArrayArray2OK3", predicateArrayArray2.resolve(exchangeArrayArray2OK3));

        var exchangeArrayArray2KO1 = exchangeWithBsonContent("{ 'array': [] }");
        var exchangeArrayArray2KO2 = exchangeWithBsonContent("{ 'array': [ 1, 2, 3 ] }");
        var exchangeArrayArray2KO3 = exchangeWithBsonContent("{ 'array2': [ {'bar': true}, {'foo': false} ] }");

        Assert.assertFalse("check negative predicate ArrayArray2KO1", predicateArrayArray2.resolve(exchangeArrayArray2KO1));
        Assert.assertFalse("check negative predicate ArrayArray2KO2", predicateArrayArray2.resolve(exchangeArrayArray2KO2));
        Assert.assertFalse("check negative predicate ArrayArray2KO3", predicateArrayArray2.resolve(exchangeArrayArray2KO3));
    }

    private HttpServerExchange exchangeWithBsonContent(String content) {
        var exchange = new HttpServerExchange();
        var bsonContent = BsonUtils.parse(content);

        exchange.setRequestMethod(HttpString.tryFromString("GET"));
        exchange.setRequestPath("http://127.0.0.1/softinstigate/coll");
        exchange.setRelativePath("/softinstigate/coll");

        return withBson(exchange, bsonContent);
    }
}

