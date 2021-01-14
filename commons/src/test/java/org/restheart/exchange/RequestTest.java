/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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

package org.restheart.exchange;

import io.undertow.server.HttpServerExchange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTest {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTest.class);

    /**
     *
     */
    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    @Test
    public void testGetPath() {
        var ex = mock(HttpServerExchange.class);
        var path = "/foo/bar";
        when(ex.getRequestPath()).thenReturn(path);

        var req = ByteArrayRequest.init(ex);

        assertEquals(path, req.getPath());
        var params = req.getPathParams("/{one}/{two}");
        assertEquals("foo", params.get("one"));
        assertEquals("bar", params.get("two"));

        var params2 = req.getPathParams("/{one}/*");
        assertEquals("foo", params2.get("one"));

        var params3 = req.getPathParams("/{one}/{two}/*");
        assertNull(params3.get("one"));
        assertNull(params3.get("two"));
    }
}
