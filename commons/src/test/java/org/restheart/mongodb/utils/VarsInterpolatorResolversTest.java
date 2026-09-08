/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.mongodb.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.utils.BsonUtils;

import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;

/**
 * A {@code $var}/{@code $arg} naming a {@code @} variable resolves through the registered
 * {@link org.restheart.security.VarResolver}s (restheart#727), so a GraphQL mapping — or anything
 * else interpolating with a request — can use the same variables an ACL can.
 *
 * <p>Without a request there is nothing to resolve against, which is the case these tests can
 * exercise directly: the supplied values still win, and an unknown name still fails.
 */
public class VarsInterpolatorResolversTest {

    private static BsonValueOrThrow interpolate(String doc, BsonDocument values) {
        return () -> VarsInterpolator.interpolate(VAR_OPERATOR.$arg, BsonDocument.parse(doc), values, null);
    }

    private interface BsonValueOrThrow {
        org.bson.BsonValue get() throws Exception;
    }

    @Test
    public void theSuppliedValuesResolveAsBefore() throws Exception {
        var result = interpolate("{ \"author\": { \"$arg\": \"who\" } }",
                new BsonDocument("who", new BsonString("Calvino"))).get();

        assertEquals(new BsonString("Calvino"), result.asDocument().get("author"));
    }

    @Test
    public void aCallerCannotSupplyAValueForAResolvedVariable() {
        // The security-relevant one. An aggregation binds its $vars from ?avars, which the caller
        // writes, so if a supplied value could stand in for @user then
        // ?avars={"@user":{"userid":"admin"}} would override the identity the pipeline scopes
        // itself by. A @ name is resolved by the server or not at all — here there is no request,
        // so it stays unbound rather than taking the caller's word for it.
        assertThrows(QueryVariableNotBoundException.class,
                () -> interpolate("{ \"owner\": { \"$arg\": \"@user\" } }",
                        new BsonDocument("@user", new BsonString("supplied"))).get());
    }

    @Test
    public void anUnknownVariableIsStillUnbound_notTheLiteralString() {
        // the failure this must not have: a mistyped @usr._id silently becoming the string
        // "@usr._id" and matching a document
        assertThrows(QueryVariableNotBoundException.class,
                () -> interpolate("{ \"owner\": { \"$arg\": \"@usr._id\" } }", new BsonDocument()).get());
    }

    @Test
    public void aDefaultStillAppliesWhenNothingResolves() throws Exception {
        var result = interpolate("{ \"limit\": { \"$arg\": [\"size\", 10] } }", new BsonDocument()).get();

        assertEquals(10, result.asDocument().getNumber("limit").intValue());
    }

    @Test
    public void withoutARequestAResolvedVariableIsUnbound() {
        // fail closed: the only fallback available would be a caller-supplied value
        assertThrows(QueryVariableNotBoundException.class,
                () -> interpolate("{ \"t\": { \"$arg\": \"@now\" } }", new BsonDocument()).get());
    }

    @Test
    public void anOrdinaryNameIsUnaffected() throws Exception {
        // only @ names change; everything else resolves from the supplied values as before
        var result = interpolate("{ \"a\": { \"$arg\": \"user\" } }",
                new BsonDocument("user", new BsonString("plain"))).get();

        assertEquals(new BsonString("plain"), result.asDocument().get("a"));
    }

    @Test
    public void nestedStructuresAreWalked() throws Exception {
        var result = interpolate("{ \"$and\": [ { \"a\": { \"$arg\": \"x\" } } ] }",
                new BsonDocument("x", new BsonString("v"))).get();

        assertTrue(BsonUtils.toJson(result).contains("\"v\""), BsonUtils.toJson(result));
    }
}
