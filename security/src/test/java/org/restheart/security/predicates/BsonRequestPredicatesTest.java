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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.restheart.exchange.ExchangeWithRequestFactory.withBson;

import org.junit.jupiter.api.Test;
import org.restheart.security.utils.MongoUtils;
import org.restheart.utils.BsonUtils;

import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

public class BsonRequestPredicatesTest {
    @Test
    public void testBsonRequestContainsBson() {
        final var predicateFooArray = PredicateParser.parse("bson-request-contains(foo, array)",
                MongoUtils.class.getClassLoader());
        final var predicateFooArrayNotExisting = PredicateParser.parse("bson-request-contains(foo, array, notex)",
                MongoUtils.class.getClassLoader());
        final var predicateFooBar = PredicateParser.parse("bson-request-contains(foo.bar)",
                MongoUtils.class.getClassLoader());
        final var predicateArray = PredicateParser.parse("bson-request-contains(array)",
                MongoUtils.class.getClassLoader());
        final var predicateArrayA = PredicateParser.parse("bson-request-contains(array.a)",
                MongoUtils.class.getClassLoader());
        final var predicateArrayB = PredicateParser.parse("bson-request-contains(array.b)",
                MongoUtils.class.getClassLoader());
        final var predicateBar = PredicateParser.parse("bson-request-contains(bar)",
                MongoUtils.class.getClassLoader());
        final var predicateBarFoo = PredicateParser.parse("bson-request-contains(bar.foo)",
                MongoUtils.class.getClassLoader());
        final var predicateUpdateOperators = PredicateParser.parse("bson-request-contains(p.q)",
                MongoUtils.class.getClassLoader());

        final var exchange = exchangeWithBsonContent(
                "{ 'foo':{'bar':true}, 'array': [{'a':1,'b':2},{'b':1}], '$set': {'p.q.r': true} }");

        assertTrue(predicateFooArray.resolve(exchange), "check positive predicate foo,array");
        assertTrue(predicateFooBar.resolve(exchange), "check positive predicate foo.bar");
        assertTrue(predicateArray.resolve(exchange), "check positive predicate array");
        assertTrue(predicateArrayB.resolve(exchange), "check positive predicate array.b");

        assertTrue(predicateUpdateOperators.resolve(exchange),
                "check positive predicate update operators");

        assertFalse(predicateArrayA.resolve(exchange), "check negative predicate array.a");
        assertFalse(
                predicateFooArrayNotExisting.resolve(exchange),
                "check negative predicate foo,array,notex");
        assertFalse(predicateBar.resolve(exchange), "check negative predicate");
        assertFalse(predicateBarFoo.resolve(exchange), "check negative predicate");
    }

    @Test
    public void testBsonRequestWhitelistBson() {
        final var predicate = PredicateParser.parse("bson-request-whitelist(a, c, us.d, d.n)",
                MongoUtils.class.getClassLoader());
        final var predicateEmpty = PredicateParser.parse("bson-request-whitelist()",
                MongoUtils.class.getClassLoader());

        final var exchangeOK1 = exchangeWithBsonContent("{ 'a': {'b': 1}, 'c': true, '$unset': {'us.d': true} }");
        final var exchangeOK2 = exchangeWithBsonContent("{ 'd.n': true }");
        final var exchangeOK3 = exchangeWithBsonContent("{ 'd.n.foo': true }");
        final var exchangeEmpty = exchangeWithBsonContent("{}");

        assertTrue(predicate.resolve(exchangeOK1), "check positive predicate");
        assertTrue(predicate.resolve(exchangeOK2), "check positive predicate");
        assertTrue(predicate.resolve(exchangeOK3), "check positive predicate");

        final var exchangeKO1 = exchangeWithBsonContent("{ 'other': {'b': 1} }");
        final var exchangeKO2 = exchangeWithBsonContent("{ 'us': {'b': 1} }");
        final var exchangeKO3 = exchangeWithBsonContent("{ 'us': true }");
        final var exchangeKO4 = exchangeWithBsonContent("{ 'd': true }");

        assertFalse(predicate.resolve(exchangeKO1), "check negative predicate 1");
        assertFalse(predicate.resolve(exchangeKO2), "check negative predicate 2");
        assertFalse(predicate.resolve(exchangeKO3), "check negative predicate 2");
        assertFalse(predicate.resolve(exchangeKO4), "check negative predicate 2");

        assertFalse(predicateEmpty.resolve(exchangeKO1), "check negative predicate 2");
        assertTrue(predicateEmpty.resolve(exchangeEmpty), "check negative predicate 2");

    }

    @Test
    public void testBsonRequestBlacklistBson() {
        final var predicateFooArray = PredicateParser.parse("bson-request-blacklist(foo, array)", MongoUtils.class.getClassLoader());

        final var exchangeFooArrayOK = exchangeWithBsonContent("{ 'good': true }");

        assertTrue(predicateFooArray.resolve(exchangeFooArrayOK), "check positive predicate FooArray");

        final var exchangeFooArrayKO1 = exchangeWithBsonContent("{ 'foo': {'bar':true} }");
        final var exchangeFooFooArrayKO2 = exchangeWithBsonContent("{ 'foo.bar': true }");
        final var exchangeFooFooArrayKO3 = exchangeWithBsonContent("{ 'foo': null }");

        assertFalse(
                predicateFooArray.resolve(exchangeFooArrayKO1),
                "check negative predicate FooFooArrayKO1");
        assertFalse(
                predicateFooArray.resolve(exchangeFooFooArrayKO2),
                "check negative predicate FooFooArrayKO2");
        assertFalse(
                predicateFooArray.resolve(exchangeFooFooArrayKO3),
                "check negative predicate FooFooArrayKO3");

        final var predicateFooDotBar = PredicateParser.parse("bson-request-blacklist(foo.bar)",
                MongoUtils.class.getClassLoader());

        final var exchangeFooDotBarOK1 = exchangeWithBsonContent("{ 'foo': true }");
        final var exchangeFooDotBarOK2 = exchangeWithBsonContent("{ 'foo': { 'other': true } }");

        assertTrue(predicateFooDotBar.resolve(exchangeFooDotBarOK1), "check positive predicate good");
        assertTrue(predicateFooDotBar.resolve(exchangeFooDotBarOK2), "check positive predicate good");

        final var exchangeFooDotBarKO1 = exchangeWithBsonContent("{ 'foo.bar': true }");
        final var exchangeFooDotBarKO2 = exchangeWithBsonContent("{ 'foo': { 'bar': { 'more': true}} }");
        final var exchangeFooDotBarKO3 = exchangeWithBsonContent("{ 'foo.bar.more': true }");

        assertFalse(
                predicateFooDotBar.resolve(exchangeFooDotBarKO1),
                "check negative predicate FooDotBarKO1");
        assertFalse(
                predicateFooDotBar.resolve(exchangeFooDotBarKO2),
                "check negative predicate FooDotBarKO2");
        assertFalse(
                predicateFooDotBar.resolve(exchangeFooDotBarKO3),
                "check negative predicate FooDotBarKO3");

        final var predicateArrayArray2 = PredicateParser.parse("bson-request-blacklist(array, array2.foo)",
                MongoUtils.class.getClassLoader());

        final var exchangeArrayArray2OK1 = exchangeWithBsonContent("{ 'array2': [] }");
        final var exchangeArrayArray2OK2 = exchangeWithBsonContent("{ 'array2': [ {'bar': true}, {'bar': false} ] }");
        final var exchangeArrayArray2OK3 = exchangeWithBsonContent("{ 'array2': [ 1, 2, 3 ] }");

        assertTrue(predicateArrayArray2.resolve(exchangeArrayArray2OK1),
                "check positive predicate ArrayArray2OK1");
        assertTrue(predicateArrayArray2.resolve(exchangeArrayArray2OK2),
                "check positive predicate ArrayArray2OK2");
        assertTrue(predicateArrayArray2.resolve(exchangeArrayArray2OK3),
                "check positive predicate ArrayArray2OK3");

        final var exchangeArrayArray2KO1 = exchangeWithBsonContent("{ 'array': [] }");
        final var exchangeArrayArray2KO2 = exchangeWithBsonContent("{ 'array': [ 1, 2, 3 ] }");
        final var exchangeArrayArray2KO3 = exchangeWithBsonContent("{ 'array2': [ {'bar': true}, {'foo': false} ] }");

        assertFalse(
                predicateArrayArray2.resolve(exchangeArrayArray2KO1),
                "check negative predicate ArrayArray2KO1");
        assertFalse(
                predicateArrayArray2.resolve(exchangeArrayArray2KO2),
                "check negative predicate ArrayArray2KO2");
        assertFalse(
                predicateArrayArray2.resolve(exchangeArrayArray2KO3),
                "check negative predicate ArrayArray2KO3");
    }

    private HttpServerExchange exchangeWithBsonContent(final String content) {
        final var exchange = new HttpServerExchange();
        final var bsonContent = BsonUtils.parse(content);

        exchange.setRequestMethod(HttpString.tryFromString("GET"));
        exchange.setRequestPath("http://127.0.0.1/softinstigate/coll");
        exchange.setRelativePath("/softinstigate/coll");

        return withBson(exchange, bsonContent);
    }

    @Test
    public void testBsonRequestPropEqualsStrings() {
        final var exchangeFooEqBar = exchangeWithBsonContent("""
                {
                    "foo": "bar"
                }
                """);

        final var exchangeFooEqZap = exchangeWithBsonContent("""
                {
                    "foo": "zap"
                }
                """);

        final var predicate = PredicateParser.parse("bson-request-prop-equals(key=foo, value='\"bar\"')", MongoUtils.class.getClassLoader());

        assertTrue(
                predicate.resolve(exchangeFooEqBar),
                "check foo=bar equals bar");

        assertFalse(
                predicate.resolve(exchangeFooEqZap),
                "check foo=zap equals bar");
    }

    @Test
    public void testBsonRequestPropEqualsSubdoc() {
        final var exchangeFooEqBar = exchangeWithBsonContent("""
                {
                    "sub": { "foo": "bar" }
                }
                """);

        final var exchangeFooEqZap = exchangeWithBsonContent("""
                {
                    "sub": { "foo": "zap" }
                }
                """);

        final var predicate = PredicateParser.parse("bson-request-prop-equals(key=sub.foo, value='\"bar\"')", MongoUtils.class.getClassLoader());

        assertTrue(
                predicate.resolve(exchangeFooEqBar),
                "check sub.foo=bar equals bar");

        assertFalse(
                predicate.resolve(exchangeFooEqZap),
                "check sub.foo=zap equals bar");

        final var predicateOnDoc = PredicateParser.parse("bson-request-prop-equals(key=sub, value='{ \"foo\": \"bar\" }')", MongoUtils.class.getClassLoader());

        assertTrue(
                predicateOnDoc.resolve(exchangeFooEqBar),
                "check sub={'foo':'bar'} equals {'foo':'bar'}");

        assertFalse(
                predicateOnDoc.resolve(exchangeFooEqZap),
                "check sub={'foo':'zap'} equals {'foo':'bar'}");
    }

    @Test
    public void testBsonRequestArrayContains() {
        final var exchangeBarAndFoo = exchangeWithBsonContent("""
                {
                    "a": [ "bar", "foo" ]
                }
                """);

        final var exchangeFooAndBaz = exchangeWithBsonContent("""
                {
                    "a": [ "foo", "baz" ]
                }
                """);

        final var predicate = PredicateParser.parse("bson-request-array-contains(key=a, values='\"bar\"')", MongoUtils.class.getClassLoader());

        assertTrue(
                predicate.resolve(exchangeBarAndFoo),
                "check [ \"bar\", \"zap\" ] contains bar");

        assertFalse(
                predicate.resolve(exchangeFooAndBaz),
                "check foo=[ \"foo\", \"baz\" ] contains bar");

        final var predicateArray = PredicateParser.parse("bson-request-array-contains(key=a, values={'\"bar\"', '\"foo\"'})", MongoUtils.class.getClassLoader());

        assertTrue(
                predicateArray.resolve(exchangeBarAndFoo),
                "check [ \"bar\", \"foo\" ] contains { bar, foo }");

        assertFalse(
                predicateArray.resolve(exchangeFooAndBaz),
                "check foo=[ \"foo\", \"zap\" ] contains { bar, zap }");
    }

    @Test
    public void testBsonRequestArrayContainsDotNotation() {
        final var exchangeBarAndFoo = exchangeWithBsonContent("""
                {
                    "sub": { "a": [ "bar", "foo" ] }
                }
                """);

        final var exchangeFooAndBaz = exchangeWithBsonContent("""
                {
                    "sub": { "a": [ "foo", "baz" ] }
                }
                """);

        final var predicate = PredicateParser.parse("bson-request-array-contains(key=sub.a, values='\"bar\"'')", MongoUtils.class.getClassLoader());

        assertTrue(
                predicate.resolve(exchangeBarAndFoo),
                "check [ \"bar\", \"foo\" ] contains bar using the dot notation");

        assertFalse(
                predicate.resolve(exchangeFooAndBaz),
                "check [ \"foo\", \"baz\" ] contains bar using the dot notation");

        final var predicateArray = PredicateParser.parse("bson-request-array-contains(key=sub.a, values={'\"bar\"', '\"foo\"'})", MongoUtils.class.getClassLoader());

        assertTrue(
            predicateArray.resolve(exchangeBarAndFoo),
            "check [ \"bar\", \"foo\" ] contains { bar, foo } using the dot notation");

        assertFalse(
                predicateArray.resolve(exchangeFooAndBaz),
                "check foo=[ \"foo\", \"baz\" ] contains { bar, foo } using the dot notation");
    }

    @Test
    public void testBsonRequestArrayIsSubset() {
        final var exchangeBarAndFoo = exchangeWithBsonContent("""
                {
                    "a": [ "bar", "foo" ]
                }
                """);

        final var exchangeFooAndBaz = exchangeWithBsonContent("""
                {
                    "a": [ "foo", "baz" ]
                }
                """);

        final var predicate = PredicateParser.parse("bson-request-array-is-subset(key=a, values='\"bar\"')", MongoUtils.class.getClassLoader());

        assertFalse(
                predicate.resolve(exchangeBarAndFoo),
                "check [ \"bar\", \"zap\" ] is subset of bar");

        assertFalse(
                predicate.resolve(exchangeFooAndBaz),
                "check foo=[ \"foo\", \"baz\" ] is subset bar");

        final var predicateArray = PredicateParser.parse("bson-request-array-is-subset(key=a, values={'\"bar\"', '\"foo\"'})", MongoUtils.class.getClassLoader());

        assertTrue(
                predicateArray.resolve(exchangeBarAndFoo),
                "check [ \"bar\", \"foo\" ] is subset of { bar, foo }");

        assertFalse(
                predicateArray.resolve(exchangeFooAndBaz),
                "check [ \"foo\", \"baz\" ] is subset of { bar, foo }");
    }
}
