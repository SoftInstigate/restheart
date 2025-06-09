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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
