/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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

import org.junit.jupiter.api.Test;
import org.restheart.security.utils.MongoUtils;

import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

public class QParamsPredicatesTest {
    @Test
    public void testQparamsSize() {
        var predicateT = PredicateParser.parse("qparams-size(2)", MongoUtils.class.getClassLoader());
        var predicateF = PredicateParser.parse("qparams-size(3)", MongoUtils.class.getClassLoader());

        var exchange = exchangeWithQParams("foo", "bar");

        assertTrue(predicateT.resolve(exchange), "check positive predicate");
        assertFalse(predicateF.resolve(exchange), "check negative predicate");

        var predicate0 = PredicateParser.parse("qparams-size(0)", MongoUtils.class.getClassLoader());

        var exchangeNQP = exchangeWithQParams();
        assertTrue(predicate0.resolve(exchangeNQP), "check positive predicate");
        assertFalse(predicate0.resolve(exchange), "check positive predicate");
    }

    @Test
    public void testQparamsContain() {
        var predicateT1 = PredicateParser.parse("qparams-contain(foo, bar)", MongoUtils.class.getClassLoader());
        var predicateT2 = PredicateParser.parse("qparams-contain(foo)", MongoUtils.class.getClassLoader());
        var predicateF1 = PredicateParser.parse("qparams-contain(other)", MongoUtils.class.getClassLoader());
        var predicateF2 = PredicateParser.parse("qparams-contain(foo, other)", MongoUtils.class.getClassLoader());

        var exchange = exchangeWithQParams("foo", "bar");

        assertTrue(predicateT1.resolve(exchange), "check positive predicate");
        assertTrue(predicateT2.resolve(exchange), "check positive predicate");
        assertFalse(predicateF1.resolve(exchange), "check negative predicate");
        assertFalse(predicateF2.resolve(exchange), "check negative predicate");
    }

    @Test
    public void testQparamsWhitelist() {
        var predicate = PredicateParser.parse("qparams-whitelist(foo, bar)", MongoUtils.class.getClassLoader());
        var predicateNOQP = PredicateParser.parse("qparams-whitelist()", MongoUtils.class.getClassLoader());

        var exchangeT1 = exchangeWithQParams("foo", "bar");
        var exchangeT2 = exchangeWithQParams();
        var exchangeF1 = exchangeWithQParams("other");
        var exchangeF2 = exchangeWithQParams("foo", "other");

        assertTrue(predicate.resolve(exchangeT1), "check positive predicate");
        assertTrue(predicate.resolve(exchangeT2), "check positive predicate");
        assertFalse(predicate.resolve(exchangeF1), "check negative predicate");
        assertFalse(predicate.resolve(exchangeF2), "check negative predicate");

        assertFalse(predicateNOQP.resolve(exchangeF1), "check negative predicate");
    }

    @Test
    public void testQparamsBlacklist() {
        var predicate = PredicateParser.parse("qparams-blacklist(foo, bar)", MongoUtils.class.getClassLoader());

        var exchangeT1 = exchangeWithQParams("other", "another-other");
        var exchangeT2 = exchangeWithQParams();
        var exchangeF1 = exchangeWithQParams("other", "foo");
        var exchangeF2 = exchangeWithQParams("foo");

        assertTrue(predicate.resolve(exchangeT1), "check positive predicate");
        assertTrue(predicate.resolve(exchangeT2), "check positive predicate");
        assertFalse(predicate.resolve(exchangeF1), "check negative predicate");
        assertFalse(predicate.resolve(exchangeF2), "check negative predicate");
    }

    private HttpServerExchange exchangeWithQParams(String... qparams) {
        var exchange = new HttpServerExchange();
        exchange.setRequestMethod(HttpString.tryFromString("GET"));

        for (var qparam : qparams) {
            exchange.addQueryParam(qparam, "foo");
        }

        String queryString;

        if (qparams == null || qparams.length == 0) {
            queryString = "";
        } else if (qparams.length == 1) {
            queryString = "?".concat(qparams[0]).concat("=foo");
            ;
        } else {
            queryString = "?".concat(qparams[0]).concat("=foo");

            for (var idx = 1; idx < qparams.length; idx++) {
                queryString = queryString.concat("&").concat(qparams[idx]).concat("=foo");
            }
        }

        exchange.setRequestPath("http://127.0.0.1/softinstigate/coll".concat(queryString));
        exchange.setRelativePath("/softinstigate/coll".concat(queryString));
        exchange.setQueryString(queryString);

        return exchange;
    }
}
