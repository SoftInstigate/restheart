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
package org.restheart.security.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.JsonProxyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class JsonRequestTest {

    private static final Logger LOG = LoggerFactory.getLogger(JsonRequestTest.class);

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

//    @Rule
//    public TestRule watcher = new TestWatcher() {
//        @Override
//        protected void starting(Description description) {
//            LOG.info("executing test {}", description.toString());
//        }
//    };
    public JsonRequestTest() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testSelectRequestMethod() {
        var exchange = new HttpServerExchange();
        var request = JsonProxyRequest.of(exchange);

        exchange.setRequestMethod(new HttpString("UNKNOWN"));
        assertEquals(METHOD.OTHER, request.getMethod());

        exchange.setRequestMethod(new HttpString("GET"));
        assertEquals(METHOD.GET, request.getMethod());

        exchange.setRequestMethod(new HttpString("PATCH"));
        assertEquals(METHOD.PATCH, request.getMethod());
    }
}
