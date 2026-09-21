/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;
import org.restheart.mongodb.MongoServiceConfigurationKeys;
import org.restheart.utils.URLUtils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

/**
 * The URL a {@code Location} header names (#749): behind a TLS-terminating proxy the exchange is
 * plain http, and the header must not say so.
 */
public class MongoURLUtilsRemappedRequestURLTest {

    /** A POST to /db/coll as it reaches the node, with these headers. This module's stub fixes its own. */
    private static HttpServerExchange post(String... headers) {
        var exchange = spy(new HttpServerExchange());
        exchange.setRequestScheme("http");
        exchange.setRequestURI("/db/coll");
        exchange.setRelativePath("/coll");

        var map = new HeaderMap();
        for (int i = 0; i < headers.length; i += 2) {
            map.put(HttpString.tryFromString(headers[i]), headers[i + 1]);
        }
        when(exchange.getRequestHeaders()).thenReturn(map);

        return exchange;
    }

    @Test
    public void behindATlsProxyTheForwardedSchemeIsUsed() {
        var exchange = post("Host", "api.example.com", "X-Forwarded-Proto", "https");
        assertEquals("https://api.example.com/db/coll", MongoURLUtils.remappedRequestURL(null, exchange));
    }

    @Test
    public void withNoProxyTheRequestItself() {
        var exchange = post("Host", "localhost:8080");
        assertEquals("http://localhost:8080/db/coll", MongoURLUtils.remappedRequestURL(null, exchange));
    }

    @Test
    public void theInstanceBaseUrlAttachedForTheTenantWinsOverTheConfiguredOne() {
        // a shared node: the proxy's headers name the node, the deployment knows the tenant
        var exchange = post("Host", "node.internal");
        exchange.putAttachment(Request.ATTACHED_PARAMS_KEY,
                new HashMap<>(Map.of(MongoServiceConfigurationKeys.INSTANCE_BASE_URL_OVERRIDE, "https://t1.example.com/")));

        assertEquals("https://t1.example.com/coll", MongoURLUtils.remappedRequestURL("https://configured.example.com/api", exchange));
    }

    @Test
    public void theMcpBaseUrlIsNotTheMongoServices() {
        // the MCP server's own override names the tenant for MCP; the Location follows the mongo one
        var exchange = post("Host", "localhost:8080");
        exchange.putAttachment(Request.ATTACHED_PARAMS_KEY,
                new HashMap<>(Map.of(URLUtils.PUBLIC_BASE_URL_OVERRIDE, "https://t1.example.com")));

        assertEquals("http://localhost:8080/db/coll", MongoURLUtils.remappedRequestURL(null, exchange));
    }

    @Test
    public void theInstanceBaseUrlNamesTheServiceMountAsBefore() {
        var exchange = post("Host", "node.internal", "X-Forwarded-Proto", "https");
        assertEquals("https://api.example.com/mongo/coll", MongoURLUtils.remappedRequestURL("https://api.example.com/mongo/", exchange));
    }

    @Test
    public void withNoHostTheExchangeKnowsWhereItWasReached() {
        var exchange = post();
        assertEquals("http://connection-address/db/coll", MongoURLUtils.remappedRequestURL(null, exchange));
    }
}
