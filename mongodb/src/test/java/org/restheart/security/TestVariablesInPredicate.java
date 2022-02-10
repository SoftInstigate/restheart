/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
