/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.MongoRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AclVarsInterpolatorTest {

    private static final Logger LOG = LoggerFactory.getLogger(AclVarsInterpolatorTest.class);

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    public AclVarsInterpolatorTest() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testRemoveUnboundVariablesOne() {
        var prefix = "@user";
        var predicate = "path-template(/user/{tenant}/*) and in(${tenant), @user.tenants )";

        assertFalse(AclVarsInterpolator.removeUnboundVariables(prefix, predicate).contains("@user"));
    }

    @Test
    public void testRemoveUnboundVariablesTwo() {
        var prefix = "@user";
        var predicate = "path-template(/user/{tenant}/*) and in(${tenant), @user.tenants) or equal(@user.other, ${tenant})";

        assertFalse(AclVarsInterpolator.removeUnboundVariables(prefix, predicate).contains("@user"));
    }

    @Test
    public void testRemoveUnboundVariablesQuotes() {
        var prefix = "@user";
        var predicate = "path-template(@user.tenants, /user/{tenant}/*) equals(\"@user.tenants\", ${tenant})";

        assertTrue(AclVarsInterpolator.removeUnboundVariables(prefix, predicate).contains("\"@user.tenants\""));
    }

    @Test
    public void testRemoveUnboundVariablesNoVars() {
        var prefix = "@user";
        var predicate = "method(GET)";
        var expected = "method(GET)";

        assertEquals(expected, AclVarsInterpolator.removeUnboundVariables(prefix, predicate));
    }

    @Test
    public void testInterpolatePredicateWithRequestBody() {
        // Test that @request.body variables are properly interpolated in predicates
        var predicate = "method(POST) and equals(@request.body.amount, '5000')";
        var requestBody = new BsonDocument().append("amount", new BsonInt32(5000));
        
        var interpolated = AclVarsInterpolator.interpolatePredicate(predicate, "@request.body.", requestBody);
        
        // The @request.body.amount should be replaced with the actual value
        assertTrue(interpolated.contains("'5000'"));
        assertFalse(interpolated.contains("@request.body.amount"));
    }

    @Test
    public void testInterpolatePredicateWithNestedRequestBody() {
        // Test nested property access with @request.body
        var predicate = "equals(@request.body.transaction.amount, '10000')";
        var requestBody = new BsonDocument()
            .append("transaction", new BsonDocument()
                .append("amount", new BsonInt32(10000))
                .append("currency", new BsonString("EUR")));
        
        // Flatten the document for interpolation
        var flattened = org.restheart.utils.BsonUtils.flatten(requestBody, true);
        var interpolated = AclVarsInterpolator.interpolatePredicate(predicate, "@request.body.", flattened);
        
        // The nested property should be interpolated
        assertTrue(interpolated.contains("'10000'"));
        assertFalse(interpolated.contains("@request.body.transaction.amount"));
    }

    @Test
    public void testInterpolatePredicateWithArrayIndex() {
        // Test array index access with @request.body
        var predicate = "equals(@request.body.roles.0, 'user')";
        var requestBody = new BsonDocument()
            .append("_id", new BsonString("a@acme.com"))
            .append("roles", new org.bson.BsonArray(java.util.List.of(
                new BsonString("user"),
                new BsonString("admin")
            )));
        
        // Flatten the document for interpolation
        var flattened = org.restheart.utils.BsonUtils.flatten(requestBody, true);
        var interpolated = AclVarsInterpolator.interpolatePredicate(predicate, "@request.body.", flattened);
        
        // The array element should be interpolated
        assertTrue(interpolated.contains("'user'"));
        assertFalse(interpolated.contains("@request.body.roles.0"));
    }

    @Test
    public void testRndVariable256Bits() {
        var request = mock(MongoRequest.class);
        var result = AclVarsInterpolator.interpolatePropValue(request, "otp", "@rnd(256)");
        
        assertTrue(result.isString());
        assertEquals(64, result.asString().getValue().length()); // 256 bits = 64 hex chars
    }

    @Test
    public void testRndVariable128Bits() {
        var request = mock(MongoRequest.class);
        var result = AclVarsInterpolator.interpolatePropValue(request, "token", "@rnd(128)");
        
        assertTrue(result.isString());
        assertEquals(32, result.asString().getValue().length()); // 128 bits = 32 hex chars
    }

    @Test
    public void testRndVariable32Bits() {
        var request = mock(MongoRequest.class);
        var result = AclVarsInterpolator.interpolatePropValue(request, "code", "@rnd(32)");
        
        assertTrue(result.isString());
        assertEquals(8, result.asString().getValue().length()); // 32 bits = 8 hex chars
    }

    @Test
    public void testRndVariableUnique() {
        var request = mock(MongoRequest.class);
        var result1 = AclVarsInterpolator.interpolatePropValue(request, "otp", "@rnd(256)");
        var result2 = AclVarsInterpolator.interpolatePropValue(request, "otp", "@rnd(256)");
        
        assertTrue(result1.isString());
        assertTrue(result2.isString());
        assertNotEquals(result1.asString().getValue(), result2.asString().getValue());
    }

    @Test
    public void testRndVariableInvalidBits() {
        var request = mock(MongoRequest.class);
        
        // Test with bits > 4096
        var result1 = AclVarsInterpolator.interpolatePropValue(request, "token", "@rnd(5000)");
        assertTrue(result1.isNull());
        
        // Test with bits <= 0
        var result2 = AclVarsInterpolator.interpolatePropValue(request, "token", "@rnd(0)");
        assertTrue(result2.isNull());
        
        var result3 = AclVarsInterpolator.interpolatePropValue(request, "token", "@rnd(-100)");
        assertTrue(result3.isNull());
    }

    @Test
    public void testRndVariableInvalidSyntax() {
        var request = mock(MongoRequest.class);
        
        // Test with non-numeric value
        var result = AclVarsInterpolator.interpolatePropValue(request, "token", "@rnd(abc)");
        assertTrue(result.isNull());
    }

    @Test
    public void testQparamsVariableSingleQuotes() {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        var queryParams = new HashMap<String, Deque<String>>();
        var otpDeque = new ArrayDeque<String>();
        otpDeque.add("abc123");
        queryParams.put("otp", otpDeque);
        
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(queryParams);
        
        var result = AclVarsInterpolator.interpolatePropValue(request, "otp", "@qparams['otp']");
        
        assertTrue(result.isString());
        assertEquals("abc123", result.asString().getValue());
    }

    @Test
    public void testQparamsVariableDoubleQuotes() {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        var queryParams = new HashMap<String, Deque<String>>();
        var tokenDeque = new ArrayDeque<String>();
        tokenDeque.add("xyz789");
        queryParams.put("token", tokenDeque);
        
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(queryParams);
        
        var result = AclVarsInterpolator.interpolatePropValue(request, "token", "@qparams[\"token\"]");
        
        assertTrue(result.isString());
        assertEquals("xyz789", result.asString().getValue());
    }

    @Test
    public void testQparamsVariableMissing() {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(new HashMap<>());
        
        var result = AclVarsInterpolator.interpolatePropValue(request, "otp", "@qparams['otp']");
        
        assertTrue(result.isNull());
    }

    @Test
    public void testQparamsVariableEmpty() {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        var queryParams = new HashMap<String, Deque<String>>();
        var otpDeque = new ArrayDeque<String>();
        otpDeque.add("");
        queryParams.put("otp", otpDeque);
        
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(queryParams);
        
        var result = AclVarsInterpolator.interpolatePropValue(request, "otp", "@qparams['otp']");
        
        assertTrue(result.isString());
        assertEquals("", result.asString().getValue());
    }

    @Test
    public void testQparamsVariableMultipleValues() {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        var queryParams = new HashMap<String, Deque<String>>();
        var tagDeque = new ArrayDeque<String>();
        tagDeque.add("first");
        tagDeque.add("second");
        tagDeque.add("third");
        queryParams.put("tag", tagDeque);
        
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(queryParams);
        
        var result = AclVarsInterpolator.interpolatePropValue(request, "tag", "@qparams['tag']");
        
        assertTrue(result.isString());
        // Should return the first value
        assertEquals("first", result.asString().getValue());
    }

    @Test
    public void testQparamsVariableNullQueryParams() {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(null);
        
        var result = AclVarsInterpolator.interpolatePropValue(request, "otp", "@qparams['otp']");
        
        assertTrue(result.isNull());
    }

    @Test
    public void testInterpolateBsonWithRndAndQparams() {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        var queryParams = new HashMap<String, Deque<String>>();
        var otpDeque = new ArrayDeque<String>();
        otpDeque.add("user-provided-otp");
        queryParams.put("otp", otpDeque);
        
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(queryParams);
        
        var doc = new BsonDocument()
            .append("apiKey", new BsonString("@rnd(256)"))
            .append("providedOtp", new BsonString("@qparams['otp']"))
            .append("normalField", new BsonString("regular-value"));
        
        var result = AclVarsInterpolator.interpolateBson(request, doc);
        
        assertTrue(result.isDocument());
        var resultDoc = result.asDocument();
        
        // Check that @rnd(256) was replaced with a 64-character hex string
        assertTrue(resultDoc.getString("apiKey").getValue().length() == 64);
        
        // Check that @qparams['otp'] was replaced with the query parameter value
        assertEquals("user-provided-otp", resultDoc.getString("providedOtp").getValue());
        
        // Check that normal fields are unchanged
        assertEquals("regular-value", resultDoc.getString("normalField").getValue());
    }
}
