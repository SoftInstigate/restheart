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
package org.restheart.security.services;

import java.util.Map;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import static org.restheart.utils.GsonUtils.array;
import static org.restheart.utils.GsonUtils.object;
import org.restheart.utils.HttpStatus;

/**
 * OAuth 2.0 Authorization Server Metadata endpoint per RFC 8414.
 *
 * Exposes GET /.well-known/oauth-authorization-server with server metadata
 * to enable automatic discovery by OAuth 2.0 clients (API gateways, MCP
 * clients, CLI tools, etc.) without hardcoded configuration.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8414">RFC 8414</a>
 */
@RegisterPlugin(
    name = "oauthAuthorizationServerMetadataService",
    description = "OAuth 2.0 Authorization Server Metadata endpoint (RFC 8414)",
    secure = false,
    enabledByDefault = true,
    defaultURI = "/.well-known/oauth-authorization-server"
)
public class OAuthAuthorizationServerMetadataService implements JsonService {

    @Inject("config")
    private Map<String, Object> config;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    private String baseUrl = null;
    private String tokenEndpointUri = "/token";

    @OnInit
    public void init() {
        this.baseUrl = argOrDefault(config, "base-url", null);
        this.tokenEndpointUri = argOrDefault(config, "token-endpoint-uri", "/token");

        // allow unauthenticated access to this discovery endpoint
        aclRegistry.registerAllow(req -> req.getPath().equals("/.well-known/oauth-authorization-server"));
    }

    @Override
    public void handle(JsonRequest request, JsonResponse response) throws Exception {
        switch (request.getMethod()) {
            case GET -> {
                var base = resolveBaseUrl(request);

                var metadata = object()
                    .put("issuer", base)
                    .put("token_endpoint", base + tokenEndpointUri)
                    .put("token_endpoint_auth_methods_supported", array()
                        .add("client_secret_basic")
                        .add("client_secret_post"))
                    .put("grant_types_supported", array()
                        .add("password")
                        .add("client_credentials"))
                    .put("response_types_supported", array()
                        .add("token"));

                response.setContent(metadata);
                response.setStatusCode(HttpStatus.SC_OK);
            }

            case OPTIONS -> handleOptions(request);

            default -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     * Resolves the base URL from config or from the request Host header.
     */
    private String resolveBaseUrl(JsonRequest request) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }

        // Fall back to request Host header
        var exchange = request.getExchange();
        var host = exchange.getRequestHeaders().getFirst("Host");
        if (host != null) {
            var scheme = exchange.getRequestScheme();
            return scheme + "://" + host;
        }

        return "";
    }
}
