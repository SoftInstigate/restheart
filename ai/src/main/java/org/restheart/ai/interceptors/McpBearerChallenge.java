/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.interceptors;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayInterceptor;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.PluginUtils;
import org.restheart.utils.URLUtils;

import io.undertow.util.HttpString;

/**
 * Answers an unauthenticated request to {@code /mcp} with the challenge an MCP client needs to
 * discover where to authenticate.
 *
 * <p>Without it the only challenge on the 401 is {@code WWW-Authenticate: Basic}, which is true —
 * Basic works — but says nothing to a client that speaks the MCP authorization spec: it looks for
 * a {@code Bearer} challenge carrying a {@code resource_metadata} pointer, finds none, and never
 * starts the OAuth flow. Pasting a token by hand keeps working either way; this is about the
 * browser sign-in path the client would otherwise not know exists.</p>
 *
 * <p>The pointer is the RFC 9728 protected-resource metadata document for this very path, built
 * from the URL the caller used: behind a TLS-terminating proxy the listener's own scheme would
 * name {@code http://} for a service reachable only over {@code https}. The header is added only
 * when {@code oauthProtectedResourceMetadataService} is enabled — a pointer to a document this
 * node does not serve would send the client to a 404 instead of to its authorization server.</p>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9728">RFC 9728</a>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
        name = "mcpBearerChallenge",
        description = "Adds the Bearer challenge with the protected-resource metadata pointer to an unauthenticated /mcp request",
        interceptPoint = InterceptPoint.REQUEST_AFTER_FAILED_AUTH,
        enabledByDefault = true)
public class McpBearerChallenge implements ByteArrayInterceptor {
    private static final HttpString WWW_AUTHENTICATE = HttpString.tryFromString("WWW-Authenticate");
    private static final String METADATA_SERVICE = "oauthProtectedResourceMetadataService";
    private static final String WELL_KNOWN = "/.well-known/oauth-protected-resource";
    private static final String MCP_SERVICE = "mcpService";

    @Inject("registry")
    private PluginsRegistry registry;

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        var base = URLUtils.externalBaseUrl(null, request.getExchange());

        response.getHeaders().put(WWW_AUTHENTICATE,
                "Bearer resource_metadata=\"" + base + WELL_KNOWN + request.getPath() + "\"");
    }

    @Override
    public boolean resolve(ByteArrayRequest request, ByteArrayResponse response) {
        return !request.isAuthenticated()
                && isMcpRequest(request)
                && metadataServiceEnabled();
    }

    private boolean isMcpRequest(ByteArrayRequest request) {
        var service = PluginUtils.handlingService(registry, request.getExchange());

        return service != null && MCP_SERVICE.equals(PluginUtils.name(service));
    }

    private boolean metadataServiceEnabled() {
        return registry.getServices().stream()
                .anyMatch(s -> METADATA_SERVICE.equals(s.getName()) && s.isEnabled());
    }
}
