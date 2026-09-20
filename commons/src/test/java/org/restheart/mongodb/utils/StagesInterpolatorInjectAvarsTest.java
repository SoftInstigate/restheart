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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.security.JwtAccount;

import io.undertow.server.HttpServerExchange;

/**
 * Client-supplied aggregation variables must never stand in for the {@code @}-prefixed ones
 * that {@link StagesInterpolator#injectAvars} binds from the authenticated account.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
class StagesInterpolatorInjectAvarsTest {

    private static MongoRequest jwtRequest() {
        var request = mock(MongoRequest.class);
        when(request.getExchange()).thenReturn(mock(HttpServerExchange.class));
        when(request.getPage()).thenReturn(1);
        when(request.getPagesize()).thenReturn(100);
        // a JWT account exposes only its claims: there is no _id
        when(request.getAuthenticatedAccount())
                .thenReturn(new JwtAccount("alice", Set.of("user"), "{\"sub\":\"alice\"}"));
        return request;
    }

    @Test
    void clientValueForAnUnboundUserPropertyIsDropped() {
        var avars = new BsonDocument("@user._id", new BsonString("bob"));

        StagesInterpolator.injectAvars(jwtRequest(), avars);

        assertFalse(avars.containsKey("@user._id"));
        var stage = BsonDocument.parse("{\"owner\": {\"$var\": \"@user._id\"}}");
        assertThrows(QueryVariableNotBoundException.class,
                () -> VarsInterpolator.interpolate(VAR_OPERATOR.$var, stage, avars));
    }

    @Test
    void clientValueForABoundUserPropertyIsReplaced() throws Exception {
        var avars = new BsonDocument("@user.sub", new BsonString("bob"));

        StagesInterpolator.injectAvars(jwtRequest(), avars);

        assertEquals(new BsonString("alice"), avars.get("@user.sub"));
    }

    @Test
    void clientVariablesWithoutTheReservedPrefixAreKept() {
        var avars = new BsonDocument("status", new BsonString("A"));

        StagesInterpolator.injectAvars(jwtRequest(), avars);

        assertEquals(new BsonString("A"), avars.get("status"));
    }
}
