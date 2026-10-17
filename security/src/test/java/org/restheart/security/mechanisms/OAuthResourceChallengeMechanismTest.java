/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.security.mechanisms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.exchange.Request;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.security.services.OAuthProtectedResourceMetadataService;
import org.restheart.utils.URLUtils;

import io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

public class OAuthResourceChallengeMechanismTest {

    /** Stands in for the MCP server, which lives in a module this one does not depend on. */
    @RegisterPlugin(name = "mcpService", description = "test", defaultURI = "/mcp")
    public static class FakeMcpService implements JsonService {
        @Override
        public void handle(JsonRequest request, JsonResponse response) {
        }
    }

    private static PluginRecord<Service<?, ?>> record(String name, Service<?, ?> instance, Map<String, Object> conf) {
        return new PluginRecord<>(name, "test", false, true, instance.getClass().getName(), instance, conf);
    }

    private static OAuthResourceChallengeMechanism mechanism(boolean metadataEnabled, boolean mcpEnabled, Map<String, Object> mcpConf)
            throws Exception {
        var registry = mock(PluginsRegistry.class);
        when(registry.getService(OAuthResourceChallengeMechanism.METADATA_SERVICE)).thenReturn(record(
                OAuthResourceChallengeMechanism.METADATA_SERVICE, new OAuthProtectedResourceMetadataService(),
                new HashMap<>(Map.of("enabled", metadataEnabled))));

        var conf = new HashMap<String, Object>(mcpConf);
        conf.put("enabled", mcpEnabled);
        when(registry.getService(OAuthResourceChallengeMechanism.MCP_SERVICE))
                .thenReturn(record(OAuthResourceChallengeMechanism.MCP_SERVICE, new FakeMcpService(), conf));

        var m = new OAuthResourceChallengeMechanism();
        var field = OAuthResourceChallengeMechanism.class.getDeclaredField("registry");
        field.setAccessible(true);
        field.set(m, registry);
        return m;
    }

    private static HttpServerExchange request(String path, String... headers) {
        var exchange = spy(new HttpServerExchange());
        exchange.setRequestPath(path);

        var map = new HeaderMap();
        for (int i = 0;i < headers.length;i += 2) {
            map.put(HttpString.tryFromString(headers[i]), headers[i + 1]);
        }
        when(exchange.getRequestHeaders()).thenReturn(map);

        return exchange;
    }

    @Test
    public void itNeverAuthenticates() throws Exception {
        // so enabling it changes no decision about who gets in
        assertEquals(AuthenticationMechanismOutcome.NOT_ATTEMPTED,
                mechanism(true, true, Map.of()).authenticate(request("/mcp"), null));
    }

    @Test
    public void aRefusedMcpRequestIsPointedAtTheMetadataOfTheMcpServer() throws Exception {
        var exchange = request("/mcp", "Host", "t1.example.com", "X-Forwarded-Proto", "https");

        var result = mechanism(true, true, Map.of()).sendChallenge(exchange, null);

        assertEquals(401, result.getDesiredResponseCode());
        assertEquals("Bearer resource_metadata=\"https://t1.example.com/.well-known/oauth-protected-resource/mcp\"",
                exchange.getResponseHeaders().getFirst(Headers.WWW_AUTHENTICATE));
    }

    @Test
    public void theTenantAttachedByTheDeploymentNamesTheMetadata() throws Exception {
        // on a shared node the proxy's headers can say http://node; the deployment knows the tenant
        var exchange = request("/mcp", "Host", "node.internal");
        exchange.putAttachment(Request.ATTACHED_PARAMS_KEY,
                new HashMap<>(Map.of(URLUtils.PUBLIC_BASE_URL_OVERRIDE, "https://t1.example.com")));

        mechanism(true, true, Map.of()).sendChallenge(exchange, null);

        assertEquals("Bearer resource_metadata=\"https://t1.example.com/.well-known/oauth-protected-resource/mcp\"",
                exchange.getResponseHeaders().getFirst(Headers.WWW_AUTHENTICATE));
    }

    @Test
    public void aSubPathOfTheMcpServerIsCovered() throws Exception {
        var exchange = request("/mcp/something", "Host", "t1.example.com");
        assertEquals(401, mechanism(true, true, Map.of()).sendChallenge(exchange, null).getDesiredResponseCode());
    }

    @Test
    public void theMountedUriIsTheConfiguredOne() throws Exception {
        var mounted = request("/agents", "Host", "t1.example.com");
        var m = mechanism(true, true, Map.of("uri", "/agents"));

        assertEquals(401, m.sendChallenge(mounted, null).getDesiredResponseCode());
        assertEquals("Bearer resource_metadata=\"http://t1.example.com/.well-known/oauth-protected-resource/agents\"",
                mounted.getResponseHeaders().getFirst(Headers.WWW_AUTHENTICATE));

        var old = request("/mcp", "Host", "t1.example.com");
        assertEquals(200, m.sendChallenge(old, null).getDesiredResponseCode());
    }

    @Test
    public void elsewhereItSaysNothing() throws Exception {
        for (var path : new String[]{"/", "/coll", "/mcpx", "/.well-known/oauth-protected-resource/mcp"}) {
            var exchange = request(path, "Host", "t1.example.com");
            assertEquals(200, mechanism(true, true, Map.of()).sendChallenge(exchange, null).getDesiredResponseCode(), path);
            assertNull(exchange.getResponseHeaders().getFirst(Headers.WWW_AUTHENTICATE), path);
        }
    }

    @Test
    public void aClientThatAskedForNoChallengeGetsNone() throws Exception {
        var byHeader = request("/mcp", "Host", "t1.example.com", BasicAuthMechanism.SILENT_HEADER_KEY, "true");
        mechanism(true, true, Map.of()).sendChallenge(byHeader, null);
        assertNull(byHeader.getResponseHeaders().getFirst(Headers.WWW_AUTHENTICATE));

        var byParam = request("/mcp", "Host", "t1.example.com");
        byParam.addQueryParam(BasicAuthMechanism.SILENT_QUERY_PARAM_KEY, "true");
        mechanism(true, true, Map.of()).sendChallenge(byParam, null);
        assertNull(byParam.getResponseHeaders().getFirst(Headers.WWW_AUTHENTICATE));
    }

    @Test
    public void withoutTheMetadataServiceThereIsNothingToPointAt() throws Exception {
        var exchange = request("/mcp", "Host", "t1.example.com");
        assertEquals(200, mechanism(false, true, Map.of()).sendChallenge(exchange, null).getDesiredResponseCode());
        assertNull(exchange.getResponseHeaders().getFirst(Headers.WWW_AUTHENTICATE));
    }

    @Test
    public void withoutTheMcpServiceThereIsNothingToProtect() throws Exception {
        var exchange = request("/mcp", "Host", "t1.example.com");
        assertEquals(200, mechanism(true, false, Map.of()).sendChallenge(exchange, null).getDesiredResponseCode());
        assertNull(exchange.getResponseHeaders().getFirst(Headers.WWW_AUTHENTICATE));
    }
}
