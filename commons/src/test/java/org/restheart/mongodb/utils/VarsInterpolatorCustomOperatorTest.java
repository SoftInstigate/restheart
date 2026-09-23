/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.util.Set;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.security.JwtAccount;
import io.undertow.server.HttpServerExchange;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;

/**
 * Tests {@link VarsInterpolator}'s dispatch of registered {@link CustomOperator}s —
 * registration mechanics themselves are covered by {@code CustomOperatorRegistryImplTest}.
 */
class VarsInterpolatorCustomOperatorTest {

    private static String uniqueName() {
        return "testop" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void customOperatorReceivesItsArgumentAlreadyVarResolved() throws Exception {
        var opName = uniqueName();
        var received = new BsonValue[1];

        CustomOperatorRegistryImpl.getInstance().register(new CustomOperator() {
            @Override
            public String name() {
                return opName;
            }

            @Override
            public BsonValue resolve(Request<?> request, BsonValue arg) {
                received[0] = arg;
                return new BsonString("resolved-by-operator");
            }
        });

        // {"$<opName>": {"$var": "query"}} — the operator must see the bound
        // value of "query", not the raw {"$var": "query"} node.
        var pipeline = new BsonDocument("$" + opName, new BsonDocument("$var", new BsonString("query")));
        var values = new BsonDocument("query", new BsonString("hello world"));

        var result = VarsInterpolator.interpolate(VAR_OPERATOR.$var, pipeline, values);

        assertEquals(new BsonString("hello world"), received[0]);
        assertEquals(new BsonString("resolved-by-operator"), result);
    }

    @Test
    void anOperatorLearnsWhichStageAndKeyItIsUsedUnder() throws Exception {
        var opName = uniqueName();
        var seen = new CustomOperator.Placement[1];
        CustomOperatorRegistryImpl.getInstance().register(new CustomOperator() {
            @Override
            public String name() {
                return opName;
            }

            @Override
            public BsonValue resolve(Request<?> request, BsonValue arg) {
                return new BsonString("without placement");
            }

            @Override
            public BsonValue resolve(Request<?> request, BsonValue arg, Placement placement) {
                seen[0] = placement;
                return new BsonString("placed");
            }
        });

        // {"$vectorScan": {"path": "bodyVector", "queryVector": {"$op": {"$var": "q"}}}}
        var stage = new BsonDocument("$vectorScan", new BsonDocument("path", new BsonString("bodyVector"))
                .append("queryVector", new BsonDocument("$" + opName, new BsonDocument("$var", new BsonString("q")))));
        var values = new BsonDocument("q", new BsonString("hello"));

        var result = VarsInterpolator.interpolate(VAR_OPERATOR.$var, stage, values);

        assertEquals(new BsonString("placed"), result.asDocument().getDocument("$vectorScan").get("queryVector"));
        assertEquals("$vectorScan", seen[0].operator());
        assertEquals("queryVector", seen[0].key());
        assertEquals("bodyVector", seen[0].argString("path"));

        // deeper under the same key, the key is kept; at the top level there is no placement
        var nested = new BsonDocument("$vectorScan", new BsonDocument("path", new BsonString("v"))
                .append("queryVector", new BsonDocument("inner", new BsonDocument("$" + opName, new BsonString("x")))));
        VarsInterpolator.interpolate(VAR_OPERATOR.$var, nested, values);
        assertEquals("queryVector", seen[0].key());

        VarsInterpolator.interpolate(VAR_OPERATOR.$var, new BsonDocument("$" + opName, new BsonString("x")), values);
        assertEquals(null, seen[0]);
    }

    @Test
    void requestIsPassedThroughToTheOperator() throws Exception {
        var opName = uniqueName();
        var req = mock(Request.class);
        var receivedRequest = new Request<?>[1];

        CustomOperatorRegistryImpl.getInstance().register(new CustomOperator() {
            @Override
            public String name() {
                return opName;
            }

            @Override
            public BsonValue resolve(Request<?> request, BsonValue arg) {
                receivedRequest[0] = request;
                return arg;
            }
        });

        var pipeline = new BsonDocument("$" + opName, new BsonString("x"));

        VarsInterpolator.interpolate(VAR_OPERATOR.$var, pipeline, new BsonDocument(), req);

        assertSame(req, receivedRequest[0]);
    }

    @Test
    void unregisteredDollarKeyIsNotInterceptedByCustomOperatorDispatch() throws Exception {
        // {"$gt": 5} looks like a single-key "$"-prefixed document exactly like a
        // registered custom operator would, but nothing is registered under "gt" —
        // it must be left completely unchanged (genuine MongoDB operator).
        var stage = new BsonDocument("$gt", new BsonInt32(5));

        var result = VarsInterpolator.interpolate(VAR_OPERATOR.$var, stage, new BsonDocument());

        assertEquals(stage, result);
    }

    // --- a @ name the server resolves to nothing is unbound, not null ---

    private static MongoRequest jwtRequest() {
        var request = mock(MongoRequest.class);
        when(request.getExchange()).thenReturn(mock(HttpServerExchange.class));
        // a JWT account carries the token's claims: it has a sub, it has no _id
        when(request.getAuthenticatedAccount())
                .thenReturn(new JwtAccount("alice", Set.of("user"), "{\"sub\":\"alice\"}"));
        return request;
    }

    @Test
    void aVarTheServerResolvesToNothingIsUnbound() {
        // not {"owner": null}, which in MongoDB matches every document without an owner: a
        // pipeline scoped by @user._id must fail rather than return what it means to exclude
        var stage = BsonDocument.parse("{\"owner\": {\"$var\": \"@user._id\"}}");

        assertThrows(QueryVariableNotBoundException.class,
                () -> VarsInterpolator.interpolate(VAR_OPERATOR.$var, stage, new BsonDocument(), jwtRequest()));
    }

    @Test
    void aVarTheServerResolvesToNothingTakesItsDefault() throws Exception {
        var stage = BsonDocument.parse("{\"owner\": {\"$var\": [\"@user._id\", \"nobody\"]}}");

        var result = VarsInterpolator.interpolate(VAR_OPERATOR.$var, stage, new BsonDocument(), jwtRequest());

        assertEquals(BsonDocument.parse("{\"owner\": \"nobody\"}"), result);
    }

    @Test
    void aVarTheServerResolvesIsStillBound() throws Exception {
        var stage = BsonDocument.parse("{\"owner\": {\"$var\": \"@user.sub\"}}");

        var result = VarsInterpolator.interpolate(VAR_OPERATOR.$var, stage, new BsonDocument(), jwtRequest());

        assertEquals(BsonDocument.parse("{\"owner\": \"alice\"}"), result);
    }
}
