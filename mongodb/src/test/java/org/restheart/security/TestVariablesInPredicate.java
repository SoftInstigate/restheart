package org.restheart.security;

import org.bson.BsonDocument;
import org.junit.Assert;
import org.junit.Test;

import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;

public class TestVariablesInPredicate {
    @Test
    public void testPredicate() {
        var raw = "path-template('/{tenant}/coll') and equals(@user.tenant, ${tenant})";
        var interpolated = AclVarsInterpolator.interpolatePredicate(raw, "@user.", testUser());

        //System.out.println("raw predicate " + raw);
        //System.out.println("interpolated predicate " + interpolated);

        var p = PredicateParser.parse(interpolated, this.getClass().getClassLoader());

        var exchange = new HttpServerExchange();
        exchange.setRequestPath("http://127.0.0.1/softinstigate/coll");
        exchange.setRelativePath("/softinstigate/coll");
        var result = p.resolve(exchange);

        Assert.assertTrue("predicate must resolve path /softinstigate/coll", result);

        var exchange2 = new HttpServerExchange();
        exchange2.setRequestPath("http://127.0.0.1/foo/coll");
        exchange2.setRelativePath("/foo/coll");
        var result2 = p.resolve(exchange2);

        Assert.assertFalse("predicate must not resolve path /foo/coll", result2);
    }

    private BsonDocument testUser() {
        return BsonDocument.parse("{ '_id': { '$oid': '6012eea28ce0797803bd9d7a'}, 'tenant': 'softinstigate' }");
    }
}