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
package org.restheart.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.restheart.exchange.MongoRequest;

/**
 * A value substituted into a predicate stays a value: whatever a query parameter carries, the
 * predicate keeps its own structure and parses.
 */
public class AclVarsPredicateValuesTest {

    private static final String PREDICATE = "equals(@qparams['a'], 'admin') and path-prefix('/never')";

    @ParameterizedTest
    @ValueSource(strings = { "admin", "x\\", "\\", "x') or true or equals('a','a", "x\\') or true or ('",
            "x\"), true, (\"", "$1", "\\$0" })
    public void aQueryParameterNeverChangesThePredicate(String value) {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        var params = new HashMap<String, Deque<String>>();
        var values = new ArrayDeque<String>();
        values.add(value);
        params.put("a", values);
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(params);

        var predicate = AclVarsInterpolator.interpolatePredicate(request, PREDICATE, getClass().getClassLoader()).toString();

        // one equals with the value as its first argument, then the path condition, nothing else
        assertTrue(predicate.startsWith("equals( {"), predicate);
        assertTrue(predicate.endsWith(", admin} ) and path-prefix( '/never' )"), predicate);
        assertTrue(predicate.indexOf(" and ") == predicate.lastIndexOf(" and "), predicate);
    }
}
