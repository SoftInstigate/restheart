/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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

import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AclVarsInterpolatorTest {

    private static final Logger LOG = LoggerFactory.getLogger(AclVarsInterpolatorTest.class);

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    public AclVarsInterpolatorTest() {
    }

    @Before
    public void setUp() {
    }

    @After
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
