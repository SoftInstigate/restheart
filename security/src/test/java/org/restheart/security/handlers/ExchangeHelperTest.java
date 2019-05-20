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
package org.restheart.security.handlers;

import org.restheart.security.handlers.exchange.JsonRequest;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class ExchangeHelperTest {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeHelperTest.class);

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

    public ExchangeHelperTest() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testSelectRequestMethod() {
        var exchange = new HttpServerExchange();
        var request = JsonRequest.wrap(exchange);

        exchange.setRequestMethod(new HttpString("UNKNOWN"));
        assertEquals(JsonRequest.METHOD.OTHER, request.getMethod());

        exchange.setRequestMethod(new HttpString("GET"));
        assertEquals(JsonRequest.METHOD.GET, request.getMethod());

        exchange.setRequestMethod(new HttpString("PATCH"));
        assertEquals(JsonRequest.METHOD.PATCH, request.getMethod());
    }
}
