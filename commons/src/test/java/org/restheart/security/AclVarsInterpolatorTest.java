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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
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

    // ── VarResolver SPI (#660) ───────────────────────────────────────────────

    private static String uniqueVarName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    /** A request stub with real attached-params storage, needed to exercise memoization. */
    private static MongoRequest requestWithAttachedParamsStore() {
        var request = mock(MongoRequest.class);
        var store = new HashMap<String, Object>();
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(request).attachParam(any(), any());
        when(request.attachedParam(any())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        return request;
    }

    @Test
    public void testCustomResolverThroughInterpolateBson() throws ConfigurationException {
        var request = mock(MongoRequest.class);
        var varName = uniqueVarName("customgreeting");

        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                return new BsonString("hello");
            }
        });

        var doc = new BsonDocument().append("greeting", new BsonString("@" + varName));
        var result = AclVarsInterpolator.interpolateBson(request, doc);

        assertEquals("hello", result.asDocument().getString("greeting").getValue());
    }

    @Test
    public void testCustomResolverThroughInterpolatePredicate() throws Exception {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var varName = uniqueVarName("customrole");

        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                return new BsonString("admin");
            }
        });

        var predicate = AclVarsInterpolator.interpolatePredicate(
                request, "equals(@" + varName + ", 'admin')", getClass().getClassLoader());

        assertTrue(predicate.resolve(exchange));
    }

    @Test
    public void testDuplicateResolverNameIsRejected() throws ConfigurationException {
        var varName = uniqueVarName("dup");
        VarResolver resolver = new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                return new BsonString("x");
            }
        };

        AclVarsRegistryImpl.getInstance().register(resolver);

        assertThrowsConfigurationException(() -> AclVarsRegistryImpl.getInstance().register(resolver));
    }

    @Test
    public void testBuiltInResolverNameCannotBeOverridden() {
        assertThrowsConfigurationException(() -> AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return "user";
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                return new BsonString("x");
            }
        }));
    }

    private static void assertThrowsConfigurationException(org.junit.jupiter.api.function.Executable executable) {
        org.junit.jupiter.api.Assertions.assertThrows(ConfigurationException.class, executable);
    }

    @Test
    public void testCustomResolverMemoizedPerRequest() throws ConfigurationException {
        var request = requestWithAttachedParamsStore();
        var callCount = new AtomicInteger();
        var varName = uniqueVarName("counter");

        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                callCount.incrementAndGet();
                return new BsonString("value");
            }
        });

        var expr = "@" + varName;
        AclVarsInterpolator.interpolatePropValue(request, "k", expr);
        AclVarsInterpolator.interpolatePropValue(request, "k", expr);

        assertEquals(1, callCount.get());
    }

    @Test
    public void testNonCacheableResolverRunsEveryTime() throws ConfigurationException {
        var request = requestWithAttachedParamsStore();
        var callCount = new AtomicInteger();
        var varName = uniqueVarName("noncacheable");

        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public boolean cacheable() {
                return false;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                return new BsonString("v" + callCount.incrementAndGet());
            }
        });

        var expr = "@" + varName;
        AclVarsInterpolator.interpolatePropValue(request, "k", expr);
        AclVarsInterpolator.interpolatePropValue(request, "k", expr);

        assertEquals(2, callCount.get());
    }

    @Test
    public void testThrowingResolverIsTreatedAsUnresolved() throws ConfigurationException {
        var request = mock(MongoRequest.class);
        var varName = uniqueVarName("broken");

        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                throw new RuntimeException("boom");
            }
        });

        var result = AclVarsInterpolator.interpolatePropValue(request, "k", "@" + varName);

        assertTrue(result.isNull());
    }

    // ── Built-in resolvers through interpolatePropValue, post-refactor (never covered before) ──

    @Test
    public void testUserVariableBareAndProperty() {
        var request = mock(MongoRequest.class);
        var properties = new BsonDocument()
                .append("_id", new BsonString("alice@example.com"))
                .append("email", new BsonString("alice@example.com"));
        var account = new MongoRealmAccount("db", "alice@example.com", "secret".toCharArray(), Set.of("user"), properties);
        when(request.getAuthenticatedAccount()).thenReturn(account);

        var bare = AclVarsInterpolator.interpolatePropValue(request, "k", "@user");
        assertTrue(bare.isDocument());
        assertEquals("alice@example.com", bare.asDocument().getString("email").getValue());

        var prop = AclVarsInterpolator.interpolatePropValue(request, "k", "@user.email");
        assertTrue(prop.isString());
        assertEquals("alice@example.com", prop.asString().getValue());
    }

    @Test
    public void testUserVariableWithNestedPropertyInReadFilterPath() {
        // @user.profile.name — two levels deep, through the readFilter/mergeRequest
        // (interpolatePropValue) path, i.e. UserVarResolver -> fromProperties() recursion.
        var request = mock(MongoRequest.class);
        var properties = new BsonDocument()
                .append("_id", new BsonString("alice@example.com"))
                .append("profile", new BsonDocument()
                        .append("name", new BsonString("Alice"))
                        .append("surname", new BsonString("Smith")));
        var account = new MongoRealmAccount("db", "alice@example.com", "secret".toCharArray(), Set.of("user"), properties);
        when(request.getAuthenticatedAccount()).thenReturn(account);

        var result = AclVarsInterpolator.interpolatePropValue(request, "k", "@user.profile.name");

        assertTrue(result.isString());
        assertEquals("Alice", result.asString().getValue());
    }

    @Test
    public void testUserVariableWithTeamRoleNestedPropertyInReadFilterPath() {
        // Mirrors the real ACL pattern used in this project's own conf-overrides.yml:
        // equals[@user.team.role, "owner"] — here through the readFilter/mergeRequest path.
        var request = mock(MongoRequest.class);
        var properties = new BsonDocument()
                .append("_id", new BsonString("alice@example.com"))
                .append("team", new BsonDocument()
                        .append("_id", new org.bson.BsonObjectId())
                        .append("role", new BsonString("owner")));
        var account = new MongoRealmAccount("db", "alice@example.com", "secret".toCharArray(), Set.of("user"), properties);
        when(request.getAuthenticatedAccount()).thenReturn(account);

        var result = AclVarsInterpolator.interpolatePropValue(request, "k", "@user.team.role");

        assertTrue(result.isString());
        assertEquals("owner", result.asString().getValue());
    }

    @Test
    public void testUserTeamRoleNestedPropertyStillWorksInPredicate() throws Exception {
        // Same real-world pattern as above, but through interpolatePredicate — the OLD
        // flatten-based @user. mechanism, untouched by this refactor. Verifies no regression.
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);
        var properties = new BsonDocument()
                .append("_id", new BsonString("alice@example.com"))
                .append("team", new BsonDocument()
                        .append("_id", new org.bson.BsonObjectId())
                        .append("role", new BsonString("owner")));
        var account = new MongoRealmAccount("db", "alice@example.com", "secret".toCharArray(), Set.of("user"), properties);
        when(request.getAuthenticatedAccount()).thenReturn(account);

        var predicate = AclVarsInterpolator.interpolatePredicate(
                request, "equals(@user.team.role, 'owner')", getClass().getClassLoader());

        assertTrue(predicate.resolve(exchange));
    }

    @Test
    public void testRequestBodyNestedPropertyInReadFilterPath() {
        // @request.body.doc.prop — two levels deep, through the readFilter/mergeRequest
        // (interpolatePropValue) path, i.e. RequestVarResolver's body branch -> BsonUtils.get().
        var request = mock(MongoRequest.class);
        var content = new BsonDocument()
                .append("doc", new BsonDocument().append("prop", new BsonString("nested-value")));
        when(request.getContent()).thenReturn(content);

        var result = AclVarsInterpolator.interpolatePropValue(request, "k", "@request.body.doc.prop");

        assertTrue(result.isString());
        assertEquals("nested-value", result.asString().getValue());
    }

    @Test
    public void testCustomResolverWithNestedDotPathWorksInPredicate() throws Exception {
        // Proves the NEW generalized predicate mechanism (interpolateOtherVars) correctly
        // captures multi-level dot paths (".a.b"), not just a single property, for any
        // resolver other than @user/@request.body.
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var varName = uniqueVarName("nested");
        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                // Only implements the exact nested path used by the test predicate, mirroring
                // how a real resolver parses its own suffix.
                return ("@" + varName + ".plan.tier").equals(var) ? new BsonString("enterprise") : null;
            }
        });

        var predicate = AclVarsInterpolator.interpolatePredicate(
                request, "equals(@" + varName + ".plan.tier, 'enterprise')", getClass().getClassLoader());

        assertTrue(predicate.resolve(exchange));
    }

    @Test
    public void testUserVariableForJwtAccountKeepsSubButStripsExpAndIss() {
        var request = mock(MongoRequest.class);
        var jwtPayload = "{\"sub\":\"alice@example.com\",\"iss\":\"restheart\",\"exp\":9999999999,\"plan\":\"pro\"}";
        var account = new JwtAccount("alice@example.com", Set.of("user"), jwtPayload);
        when(request.getAuthenticatedAccount()).thenReturn(account);

        var bare = AclVarsInterpolator.interpolatePropValue(request, "k", "@user");

        assertTrue(bare.isDocument());
        // "sub" is the user identity (mirrors "_id" for MongoRealmAccount/FileRealmAccount) and
        // must survive: predicates commonly need equals(@user._id, ...) or equals(@user.sub, ...)
        // to cover both authentication mechanisms.
        assertEquals("alice@example.com", bare.asDocument().getString("sub").getValue());
        assertEquals("pro", bare.asDocument().getString("plan").getValue());
        // "exp"/"iss" are pure JWT bookkeeping (expiry, issuer), not user identity — still stripped.
        assertTrue(bare.asDocument().get("exp") == null);
        assertTrue(bare.asDocument().get("iss") == null);
    }

    @Test
    public void testUserVariableWithNoAuthenticatedAccountIsUnresolved() {
        var request = mock(MongoRequest.class);

        assertTrue(AclVarsInterpolator.interpolatePropValue(request, "k", "@user").isNull());
        assertTrue(AclVarsInterpolator.interpolatePropValue(request, "k", "@user.email").isNull());
    }

    @Test
    public void testFilterVariable() {
        var request = mock(MongoRequest.class);
        var filterDoc = new BsonDocument().append("status", new BsonString("active"));
        when(request.getFiltersDocument()).thenReturn(filterDoc);

        var result = AclVarsInterpolator.interpolatePropValue(request, "k", "@filter");

        assertTrue(result.isDocument());
        assertEquals("active", result.asDocument().getString("status").getValue());
    }

    @Test
    public void testMongoPermissionsVariableBareAndProperty() {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var bare = AclVarsInterpolator.interpolatePropValue(request, "k", "@mongoPermissions");
        assertTrue(bare.isDocument());

        var prop = AclVarsInterpolator.interpolatePropValue(request, "k", "@mongoPermissions.allowWriteMode");
        assertTrue(prop.isBoolean());
        assertFalse(prop.asBoolean().getValue());
    }

    @Test
    public void testNowAndLegacyNowVariable() {
        var request = mock(MongoRequest.class);

        var atNow = AclVarsInterpolator.interpolatePropValue(request, "k", "@now");
        var legacyNow = AclVarsInterpolator.interpolatePropValue(request, "k", "%NOW");

        assertTrue(atNow.isDateTime());
        // @now truncates to whole seconds (Instant.now().getEpochSecond() * 1000), so comparing
        // against System.currentTimeMillis() with tight millisecond bounds is flaky by
        // construction — allow a generous window instead.
        assertTrue(Math.abs(System.currentTimeMillis() - atNow.asDateTime().getValue()) < 5000);
        assertTrue(legacyNow.isDateTime());
    }

    // ── Built-ins now usable inside a real ACL predicate string (new capability) ──────────────

    @Test
    public void testNowVariableWorksInPredicate() throws Exception {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var predicate = AclVarsInterpolator.interpolatePredicate(
                request, "not(equals(@now, '0'))", getClass().getClassLoader());

        assertTrue(predicate.resolve(exchange));
    }

    @Test
    public void testRndVariableWorksInPredicate() throws Exception {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var predicate = AclVarsInterpolator.interpolatePredicate(
                request, "not(equals(@rnd(32), 'not-gonna-match'))", getClass().getClassLoader());

        assertTrue(predicate.resolve(exchange));
    }

    @Test
    public void testMongoPermissionsVariableWorksInPredicate() throws Exception {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var predicate = AclVarsInterpolator.interpolatePredicate(
                request, "equals(@mongoPermissions.allowWriteMode, 'false')", getClass().getClassLoader());

        assertTrue(predicate.resolve(exchange));
    }

    /**
     * Regression test for a pre-existing bug (predates this refactor, never exercised before
     * because no prior test compared a boolean value inside a predicate): {@code BsonBoolean}
     * does not override {@code toString()} the way a plain {@code Boolean} does — it returns
     * {@code "BsonBoolean{value=false}"} — so formatting a resolved boolean for a predicate must
     * use {@code value.asBoolean().getValue()}, not {@code value.asBoolean().toString()}.
     */
    @Test
    public void testBooleanResolvedValueIsFormattedAsPlainTrueFalseInPredicate() throws Exception {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var varName = uniqueVarName("boolvar");
        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                return org.bson.BsonBoolean.TRUE;
            }
        });

        var predicate = AclVarsInterpolator.interpolatePredicate(
                request, "equals(@" + varName + ", 'true')", getClass().getClassLoader());

        assertTrue(predicate.resolve(exchange));
    }

    // ── Safety of the new predicate-generalization mechanism ──────────────────────────────────

    @Test
    public void testQuotedLiteralResemblingAVariableIsNotSubstituted() throws Exception {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var varName = uniqueVarName("fakevar");
        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                return new BsonString("REAL");
            }
        });

        // The first equals() is the real, unquoted variable; the second compares two occurrences
        // of the SAME text quoted, which must survive untouched. If quote-skipping were broken,
        // the substitution would corrupt the quoting (a quote-inside-a-quote) and either throw
        // while parsing or fail the comparison.
        var predicateText = "equals(@" + varName + ", 'REAL') and equals('literal @" + varName
                + " text', 'literal @" + varName + " text')";

        var predicate = AclVarsInterpolator.interpolatePredicate(request, predicateText, getClass().getClassLoader());

        assertTrue(predicate.resolve(exchange));
    }

    @Test
    public void testResolverNameDoesNotMatchAsAPrefixOfALongerName() throws Exception {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var shortName = uniqueVarName("short");
        var longName = shortName + "suffix"; // shares the short name as a literal prefix

        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return shortName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                return new BsonString("SHORT");
            }
        });
        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return longName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                return new BsonString("LONG");
            }
        });

        var predicate = AclVarsInterpolator.interpolatePredicate(
                request, "equals(@" + longName + ", 'LONG')", getClass().getClassLoader());

        assertTrue(predicate.resolve(exchange));
    }

    /**
     * End-to-end proof of the "deny, not widen" guarantee: a custom resolver used in a real ACL
     * predicate throws, and the permission — evaluated exactly the way {@code MongoAclPermission}
     * and {@code FileAclPermission} do it — denies the request rather than erroring out or
     * defaulting to allow.
     */
    @Test
    public void testThrowingResolverInPredicateDeniesTheRequest() throws Exception {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        when(request.getExchange()).thenReturn(exchange);

        var varName = uniqueVarName("boom");
        AclVarsRegistryImpl.getInstance().register(new VarResolver() {
            @Override
            public String name() {
                return varName;
            }

            @Override
            public BsonValue resolve(Request<?> req, String var) {
                throw new RuntimeException("simulated resolver failure");
            }
        });

        var requestPredicate = "equals(@" + varName + ", 'anything')";
        var classLoader = getClass().getClassLoader();

        // Mirrors exactly how MongoAclPermission/FileAclPermission build their predicate.
        java.util.function.Predicate<Request<?>> testPredicate = req -> AclVarsInterpolator
                .interpolatePredicate(req, requestPredicate, classLoader).resolve(req.getExchange());

        var permission = new BaseAclPermission(testPredicate, Set.of("user"), 0, null) {};

        assertFalse(permission.allow(request));
    }
}
