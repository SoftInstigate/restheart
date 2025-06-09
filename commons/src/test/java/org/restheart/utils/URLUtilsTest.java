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
package org.restheart.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class URLUtilsTest {

    private static final Logger LOG = LoggerFactory.getLogger(URLUtilsTest.class);

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    public URLUtilsTest() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
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
    public void testRemoveTrailingSlashesEdge() {
        String s = "/ciao/this/has/trailings/////     ";
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
