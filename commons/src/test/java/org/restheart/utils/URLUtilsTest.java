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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
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

    /** An exchange as a proxy leaves it: the headers it set, and the scheme it spoke to us over. */
    private static HttpServerExchange behindProxy(String proto, String forwardedHost, String host, String scheme) {
        var exchange = new HttpServerExchange();
        exchange.setRequestScheme(scheme);
        if (proto != null) {
            exchange.getRequestHeaders().put(HttpString.tryFromString("X-Forwarded-Proto"), proto);
        }
        if (forwardedHost != null) {
            exchange.getRequestHeaders().put(HttpString.tryFromString("X-Forwarded-Host"), forwardedHost);
        }
        if (host != null) {
            exchange.getRequestHeaders().put(Headers.HOST, host);
        }
        return exchange;
    }

    @Test
    public void externalBaseUrlPrefersWhatTheOperatorConfigured() {
        var exchange = behindProxy("https", "public.example.com", "internal:8080", "http");
        assertEquals("https://configured.example.com",
                URLUtils.externalBaseUrl("https://configured.example.com", exchange));
    }

    @Test
    public void externalBaseUrlTakesTheSchemeFromTheProxyWithoutAForwardedHost() {
        // An ALB terminates TLS, forwards to the container over plain HTTP on 8080 and preserves
        // the client's Host — it sends no X-Forwarded-Host at all. Reading the two headers only
        // as a pair named the instance http:// at the right host: a URL an HTTPS-only client
        // cannot use, which is what a customer saw in both well-known documents.
        var exchange = behindProxy("https", null, "srv.example.com", "http");
        assertEquals("https://srv.example.com", URLUtils.externalBaseUrl(null, exchange));
    }

    @Test
    public void externalBaseUrlTakesTheHostFromTheProxyWithoutAForwardedProto() {
        // A CDN in front of another origin does the opposite: Host is rewritten to the origin and
        // the real name travels in X-Forwarded-Host.
        var exchange = behindProxy(null, "srv.example.com", "origin.internal", "https");
        assertEquals("https://srv.example.com", URLUtils.externalBaseUrl(null, exchange));
    }

    @Test
    public void externalBaseUrlUsesBothWhenBothAreThere() {
        var exchange = behindProxy("https", "srv.example.com", "origin.internal", "http");
        assertEquals("https://srv.example.com", URLUtils.externalBaseUrl(null, exchange));
    }

    @Test
    public void externalBaseUrlKeepsTheFirstHopOfAForwardedProtoList() {
        var exchange = behindProxy("https, http", "srv.example.com", null, "http");
        assertEquals("https://srv.example.com", URLUtils.externalBaseUrl(null, exchange));
    }

    @Test
    public void externalBaseUrlFallsBackToTheExchangeWhenNoProxySpoke() {
        var exchange = behindProxy(null, null, "localhost:8080", "http");
        assertEquals("http://localhost:8080", URLUtils.externalBaseUrl(null, exchange));
    }

    @Test
    public void externalBaseUrlIsEmptyWithNoHostToBeHad() {
        var exchange = behindProxy("https", null, null, "http");
        assertEquals("", URLUtils.externalBaseUrl(null, exchange));
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
