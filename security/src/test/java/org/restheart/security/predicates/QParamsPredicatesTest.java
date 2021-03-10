package org.restheart.security.predicates;

import org.junit.Assert;
import org.junit.Test;
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

        Assert.assertTrue("check positive predicate", predicateT.resolve(exchange));
        Assert.assertFalse("check negative predicate", predicateF.resolve(exchange));

        var predicate0 = PredicateParser.parse("qparams-size(0)", MongoUtils.class.getClassLoader());

        var exchangeNQP = exchangeWithQParams();
        Assert.assertTrue("check positive predicate", predicate0.resolve(exchangeNQP));
        Assert.assertFalse("check positive predicate", predicate0.resolve(exchange));
    }

    @Test
    public void testQparamsContain() {
        var predicateT1 = PredicateParser.parse("qparams-contain(foo, bar)", MongoUtils.class.getClassLoader());
        var predicateT2 = PredicateParser.parse("qparams-contain(foo)", MongoUtils.class.getClassLoader());
        var predicateF1 = PredicateParser.parse("qparams-contain(other)", MongoUtils.class.getClassLoader());
        var predicateF2 = PredicateParser.parse("qparams-contain(foo, other)", MongoUtils.class.getClassLoader());

        var exchange = exchangeWithQParams("foo", "bar");

        Assert.assertTrue("check positive predicate", predicateT1.resolve(exchange));
        Assert.assertTrue("check positive predicate", predicateT2.resolve(exchange));
        Assert.assertFalse("check negative predicate", predicateF1.resolve(exchange));
        Assert.assertFalse("check negative predicate", predicateF2.resolve(exchange));
    }

    @Test
    public void testQparamsWhitelist() {
        var predicate = PredicateParser.parse("qparams-whitelist(foo, bar)", MongoUtils.class.getClassLoader());
        var predicateNOQP = PredicateParser.parse("qparams-whitelist()", MongoUtils.class.getClassLoader());

        var exchangeT1 = exchangeWithQParams("foo", "bar");
        var exchangeT2 = exchangeWithQParams();
        var exchangeF1 = exchangeWithQParams("other");
        var exchangeF2 = exchangeWithQParams("foo", "other");

        Assert.assertTrue("check positive predicate", predicate.resolve(exchangeT1));
        Assert.assertTrue("check positive predicate", predicate.resolve(exchangeT2));
        Assert.assertFalse("check negative predicate", predicate.resolve(exchangeF1));
        Assert.assertFalse("check negative predicate", predicate.resolve(exchangeF2));

        Assert.assertFalse("check negative predicate", predicateNOQP.resolve(exchangeF1));
    }

    @Test
    public void testQparamsBlacklist() {
        var predicate = PredicateParser.parse("qparams-blacklist(foo, bar)", MongoUtils.class.getClassLoader());

        var exchangeT1 = exchangeWithQParams("other", "another-other");
        var exchangeT2 = exchangeWithQParams();
        var exchangeF1 = exchangeWithQParams("other", "foo");
        var exchangeF2 = exchangeWithQParams("foo");

        Assert.assertTrue("check positive predicate", predicate.resolve(exchangeT1));
        Assert.assertTrue("check positive predicate", predicate.resolve(exchangeT2));
        Assert.assertFalse("check negative predicate", predicate.resolve(exchangeF1));
        Assert.assertFalse("check negative predicate", predicate.resolve(exchangeF2));
    }

    private HttpServerExchange exchangeWithQParams(String ...qparams) {
        var exchange = new HttpServerExchange();
        exchange.setRequestMethod(HttpString.tryFromString("GET"));

        for (var qparam: qparams) {
            exchange.addQueryParam(qparam, "foo");
        }

        String queryString;

        if (qparams == null || qparams.length == 0) {
            queryString = "";
        } else if (qparams.length == 1) {
            queryString = "?".concat(qparams[0]).concat("=foo");;
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

