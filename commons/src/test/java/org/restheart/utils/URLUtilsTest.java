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
package org.restheart.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.restheart.exchange.Request;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

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

    /** An exchange that says what it was called with: this module's stub fixes its own headers. */
    private static HttpServerExchange calledWith(String scheme, String... headers) {
        var exchange = spy(new HttpServerExchange());
        exchange.setRequestScheme(scheme);

        var map = new HeaderMap();
        for (int i = 0;i < headers.length;i += 2) {
            map.put(HttpString.tryFromString(headers[i]), headers[i + 1]);
        }
        when(exchange.getRequestHeaders()).thenReturn(map);

        return exchange;
    }

    @Test
    public void externalBaseUrl_theConfiguredValueWins() {
        var exchange = calledWith("http", "Host", "node.internal", "X-Forwarded-Proto", "https");
        assertEquals("https://api.example.com", URLUtils.externalBaseUrl("https://api.example.com", exchange));
    }

    @Test
    public void externalBaseUrl_aForwardedSchemeIsHonouredWithoutAForwardedHost() {
        // A proxy that keeps the Host it was called with sends no X-Forwarded-Host. Requiring both
        // headers answered http:// for a service published over https:// — seen in production,
        // in the protected-resource metadata of a shared node.
        var exchange = calledWith("http", "Host", "t1.example.com", "X-Forwarded-Proto", "https");
        assertEquals("https://t1.example.com", URLUtils.externalBaseUrl(null, exchange));
    }

    @Test
    public void externalBaseUrl_bothForwardedHeaders() {
        var exchange = calledWith("http", "Host", "node.internal",
                "X-Forwarded-Proto", "https", "X-Forwarded-Host", "t1.example.com");
        assertEquals("https://t1.example.com", URLUtils.externalBaseUrl(null, exchange));
    }

    @Test
    public void externalBaseUrl_theFirstSchemeOfAChainOfProxies() {
        var exchange = calledWith("http", "Host", "t1.example.com", "X-Forwarded-Proto", "https, http");
        assertEquals("https://t1.example.com", URLUtils.externalBaseUrl(null, exchange));
    }

    @Test
    public void externalBaseUrl_withNoProxyTheRequestItself() {
        var exchange = calledWith("http", "Host", "localhost:8080");
        assertEquals("http://localhost:8080", URLUtils.externalBaseUrl(null, exchange));
    }

    @Test
    public void externalBaseUrl_noHostIsNotAUrl() {
        assertEquals("", URLUtils.externalBaseUrl(null, calledWith("http")));
    }

    @Test
    public void publicBaseUrl_theAttachedOverrideWinsOverEverything() {
        // the tenant's own name, attached by a multi-tenant deployment before authentication
        var exchange = calledWith("http", "Host", "node.internal", "X-Forwarded-Proto", "http");
        exchange.putAttachment(Request.ATTACHED_PARAMS_KEY,
                new HashMap<>(Map.of(URLUtils.PUBLIC_BASE_URL_OVERRIDE, "https://t1.example.com")));

        assertEquals("https://t1.example.com", URLUtils.publicBaseUrl("https://configured.example.com", exchange));
    }

    @Test
    public void publicBaseUrl_withoutTheOverrideIsExternalBaseUrl() {
        var exchange = calledWith("http", "Host", "t1.example.com", "X-Forwarded-Proto", "https");
        assertEquals("https://t1.example.com", URLUtils.publicBaseUrl(null, exchange));
        assertEquals("https://configured.example.com", URLUtils.publicBaseUrl("https://configured.example.com", exchange));
    }
}
