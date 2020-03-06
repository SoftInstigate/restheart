/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.utils;

import io.undertow.server.HttpServerExchange;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
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
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class URLUtilsTest {

    private static final Logger LOG = LoggerFactory.getLogger(URLUtilsTest.class);

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

    public URLUtilsTest() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testRemoveTrailingSlashes() {
        String s = "/ciao/this/has/trailings/////";
        String expResult = "/ciao/this/has/trailings";
        String result = URLUtils.removeTrailingSlashes(s);
        assertEquals(expResult, result);
    }

    @Test
    public void testDecodeQueryString() {
        String qs = "one%2Btwo";
        String expResult = "one+two";
        String result = URLUtils.decodeQueryString(qs);
        assertEquals(expResult, result);
    }

    @Test
    public void testGetParentPath() {
        String path = "/a/b/c/d";
        String expResult = "/a/b/c";
        String result = URLUtils.getParentPath(path);
        assertEquals(expResult, result);
    }

    @Test
    public void testGetQueryStringRemovingParams() {
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setQueryString("a=1&b=2&c=3");
        exchange.addQueryParam("a", "1").addQueryParam("b", "2").addQueryParam("c", "3");
        String expResult = "a=1&c=3";
        String result = URLUtils.getQueryStringRemovingParams(exchange, "b");
        assertEquals(expResult, result);
    }
}
