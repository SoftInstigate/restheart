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

package org.restheart.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.undertow.server.HttpServerExchange;

public class RequestTest {

    @Test
    public void testGetPath() {
        var ex = mock(HttpServerExchange.class);
        var path = "/foo/bar";
        when(ex.getRequestPath()).thenReturn(path);
        when(ex.getRequestContentLength()).thenReturn(0l);

        var req = ByteArrayRequest.init(ex);

        assertEquals(path, req.getPath());
        var params = req.getPathParams("/{one}/{two}");
        assertEquals(params.get("one"), "foo");
        assertEquals(params.get("two"), "bar");

        var params2 = req.getPathParams("/{one}/*");
        assertEquals(params2.get("one"), "foo");

        var params3 = req.getPathParams("/{one}/{two}/*");
        assertNull(params3.get("one"));
        assertNull(params3.get("two"));
    }
}
