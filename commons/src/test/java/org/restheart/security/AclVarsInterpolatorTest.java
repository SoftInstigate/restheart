/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
