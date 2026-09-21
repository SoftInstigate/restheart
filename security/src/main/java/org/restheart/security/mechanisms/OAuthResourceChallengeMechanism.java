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

import java.util.Optional;

import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.security.services.OAuthProtectedResourceMetadataService;
import org.restheart.utils.PluginUtils;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * Tells an MCP client that is refused where to go to get in:
 * {@code WWW-Authenticate: Bearer resource_metadata="…"} on the {@code 401} of a request to the
 * MCP server.
 *
 * <p>MCP 2025-06-18 makes it a MUST for the server, and gives the client nothing else to go on.
 * MCP 2025-11-25 lets the server choose between this header and the well-known URI, and makes the
 * client try both; RESTHeart served only the well-known URI, which is why Claude connected — and
 * reconnected after its key was revoked — without it. A client that negotiated the earlier version
 * was entitled to the header and got none. Both versions are spoken here.
 *
 * <p>It never authenticates anyone: {@link #authenticate} answers {@code NOT_ATTEMPTED} to every
 * request, so adding it changes no decision about who gets in. Undertow calls
 * {@link #sendChallenge} on every mechanism of the chain and sums their headers, so this challenge
 * is added beside the others — {@code Basic realm=…} stays — and the client reads the scheme it
 * understands.
 *
 * <p>It speaks only when there is something to point at and something it protects: the
 * {@code oauthProtectedResourceMetadataService} and the {@code mcpService} both enabled, and the
 * request addressed to the MCP server's own path. Anywhere else a Bearer challenge would name a
 * resource nobody asked for. The URL is built by the metadata service itself, so the challenge and
 * the document it points at name the same host by construction — per-tenant override included.
 *
 * <p>No {@code scope} parameter. MCP recommends it, but the scopes of the flow are
 * {@code api-key:<role>}, and the role is the user's to choose on the authorization page: a scope
 * in the challenge would choose it for them.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9728#section-5.1">RFC 9728 §5.1</a>
 */
@RegisterPlugin(name = "oauthResourceChallengeMechanism",
        description = "on a 401 from the MCP server, points the client at the OAuth protected resource metadata",
        enabledByDefault = true,
        // Last: it never authenticates, so its place in the authentication order is irrelevant,
        // and last is where it can least be mistaken for something that does.
        priority = Integer.MAX_VALUE)
public class OAuthResourceChallengeMechanism implements AuthMechanism {
    static final String METADATA_SERVICE = "oauthProtectedResourceMetadataService";
    static final String MCP_SERVICE = "mcpService";

    @Inject("registry")
    private PluginsRegistry registry;

    /**
     * What the challenge needs, looked up once: the registry is complete by the first request,
     * and whether the two services are enabled and where the MCP server is mounted cannot change
     * afterwards. Empty when either service is missing or disabled, which silences the challenge.
     */
    private volatile Optional<Target> target;

    record Target(OAuthProtectedResourceMetadataService metadata, String mcpUri) {
        boolean covers(String path) {
            return path != null && (path.equals(mcpUri) || path.startsWith(mcpUri.endsWith("/") ? mcpUri : mcpUri + "/"));
        }
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange, final SecurityContext securityContext) {
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        var t = target();

        if (t.isEmpty() || !t.get().covers(exchange.getRequestPath()) || silenced(exchange)) {
            // no opinion: same answer as the mechanisms that send no challenge of their own
            return new ChallengeResult(true, 200);
        }

        var url = t.get().metadata().metadataUrl(exchange, t.get().mcpUri());
        exchange.getResponseHeaders().add(Headers.WWW_AUTHENTICATE, "Bearer resource_metadata=\"" + url + "\"");

        return new ChallengeResult(true, 401);
    }

    /**
     * The client asked for no challenge at all, with the header or the query parameter
     * {@link BasicAuthMechanism} honours. The same request, the same answer, whichever mechanism
     * would have spoken.
     */
    private static boolean silenced(final HttpServerExchange exchange) {
        return exchange.getRequestHeaders().contains(BasicAuthMechanism.SILENT_HEADER_KEY)
                || exchange.getQueryParameters().containsKey(BasicAuthMechanism.SILENT_QUERY_PARAM_KEY);
    }

    private Optional<Target> target() {
        var t = this.target;

        if (t == null) {
            t = resolve(registry);
            this.target = t;
        }

        return t;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static Optional<Target> resolve(PluginsRegistry registry) {
        var metadata = registry.getService(METADATA_SERVICE);
        var mcp = registry.getService(MCP_SERVICE);

        if (metadata == null || !metadata.isEnabled() || mcp == null || !mcp.isEnabled()
                || !(metadata.getInstance() instanceof OAuthProtectedResourceMetadataService service)) {
            return Optional.empty();
        }

        var mcpUri = PluginUtils.actualUri(mcp.getConfArgs(), (Class) mcp.getInstance().getClass());

        return mcpUri == null ? Optional.empty() : Optional.of(new Target(service, mcpUri));
    }
}
