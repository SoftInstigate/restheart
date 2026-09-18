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
import org.restheart.utils.URLUtils;

/**
 * OAuth 2.0 Protected Resource Metadata endpoint per RFC 9728.
 *
 * Exposes GET /.well-known/oauth-protected-resource (and sub-paths) with
 * resource metadata to enable automatic discovery by OAuth 2.0 clients
 * (MCP clients, API gateways, etc.) without hardcoded configuration.
 *
 * The optional path suffix after /.well-known/oauth-protected-resource
 * identifies the protected resource path and is included in the {@code resource}
 * field of the response.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9728">RFC 9728</a>
 */
@RegisterPlugin(
        name = "oauthProtectedResourceMetadataService",
        description = "OAuth 2.0 Protected Resource Metadata endpoint (RFC 9728)",
        secure = false,
        enabledByDefault = true,
        defaultURI = "/.well-known/oauth-protected-resource"
)
public class OAuthProtectedResourceMetadataService implements JsonService {

    private static final String WELL_KNOWN_PREFIX = "/.well-known/oauth-protected-resource";

    @Inject("config")
    private Map<String, Object> config;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    private String serverUrl = null;

    @OnInit
    public void init() {
        this.serverUrl = argOrDefault(config, "base-url", argOrDefault(config, "server-url", null));

        // allow unauthenticated access to this discovery endpoint and all sub-paths
        aclRegistry.registerAllow(req -> req.getPath().equals(WELL_KNOWN_PREFIX)
                || req.getPath().startsWith(WELL_KNOWN_PREFIX + "/"));
    }

    @Override
    public void handle(JsonRequest request, JsonResponse response) throws Exception {
        switch (request.getMethod()) {
            case GET -> {
                var base = resolveServerUrl(request);
                var resourcePath = extractResourcePath(request.getPath());
                var resourceUrl = resourcePath.isEmpty() ? base : base + resourcePath;

                var metadata = object()
                        .put("resource", resourceUrl)
                        .put("authorization_servers", array().add(base));

                response.setContent(metadata);
                response.setStatusCode(HttpStatus.SC_OK);
            }

            case OPTIONS -> handleOptions(request);

            default -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    /** @see URLUtils#externalBaseUrl(String, io.undertow.server.HttpServerExchange) */
    private String resolveServerUrl(JsonRequest request) {
        return URLUtils.externalBaseUrl(serverUrl, request.getExchange());
    }

    /**
     * Extracts the resource path suffix from the request path.
     * For example, {@code /.well-known/oauth-protected-resource/api/v1} → {@code /api/v1}.
     * Returns an empty string when the path is exactly the well-known prefix.
     */
    private String extractResourcePath(String requestPath) {
        if (requestPath.length() > WELL_KNOWN_PREFIX.length()
                && requestPath.startsWith(WELL_KNOWN_PREFIX + "/")) {
            return requestPath.substring(WELL_KNOWN_PREFIX.length());
        }
        return "";
    }
}
