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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.mechanisms;

import java.util.Map;

import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authenticator;
import org.restheart.security.ApiKeyCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import static io.undertow.util.Headers.AUTHORIZATION;

/**
 * Authenticates requests bearing a long-lived, opaque API key.
 *
 * <p>The key travels in the standard {@code Authorization: Bearer} header
 * (RFC 6750) rather than a scheme of its own, so every client library, curl
 * example and CI tool works without being told about a custom one.
 *
 * <h2>Sharing {@code Bearer} with {@code jwtAuthenticationMechanism}</h2>
 *
 * <p>Two mechanisms reading one scheme is an established pattern here, not a
 * conflict: {@code tokenBasicAuthMechanism} already shares {@code Basic} with
 * {@code basicAuthMechanism} by running first and declining what is not its
 * own. This does the same for {@code Bearer}.
 *
 * <p><b>The prefix is the claim.</b> A {@code Bearer} value that does not start
 * with the configured prefix is answered {@code NOT_ATTEMPTED} and falls
 * through to {@code jwtAuthenticationMechanism}, which keeps its present
 * behaviour untouched — including {@code NOT_AUTHENTICATED} for a malformed
 * JWT, so a truncated token is still reported as a bad JWT rather than as a bad
 * API key.
 *
 * <p>The ordering that makes this work is the {@code priority} below:
 * {@code PluginsFactory} sorts plugin descriptors by it and collects them with
 * {@code forEachOrdered}, and that order survives into
 * {@code AuthenticatorMechanismsHandler}. This mechanism must see a request
 * before the JWT one does, because the JWT mechanism answers
 * {@code NOT_AUTHENTICATED} — which ends the chain — for anything under
 * {@code Bearer} that does not decode as a JWT.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "apiKeyAuthMechanism",
        description = "authenticates requests bearing an API key, verified by the configured Authenticator",
        enabledByDefault = false,
        // Ahead of jwtAuthenticationMechanism (default 10). What matters is
        // only that it runs first; it claims a disjoint set of Bearer values,
        // so it competes with nothing.
        priority = Integer.MIN_VALUE)
public class ApiKeyAuthMechanism implements AuthMechanism {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiKeyAuthMechanism.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("config")
    private Map<String, Object> config;

    private Authenticator authenticator;
    private String prefix;

    @OnInit
    public void init() throws ConfigurationException {
        this.prefix = arg(config, "prefix");

        if (this.prefix == null || this.prefix.isBlank()) {
            throw new ConfigurationException(
                    "apiKeyAuthMechanism requires a non-empty 'prefix'; it is what tells an API key "
                            + "from a JWT under the same Bearer scheme");
        }

        final String authenticatorName = arg(config, "authenticator");
        final var authenticatorRecord = registry.getAuthenticator(authenticatorName);

        if (authenticatorRecord == null) {
            throw new ConfigurationException(
                    "apiKeyAuthMechanism is configured with authenticator '" + authenticatorName
                            + "' which is not enabled or does not exist");
        }

        this.authenticator = authenticatorRecord.getInstance();
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange, final SecurityContext securityContext) {
        final var authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);

        if (authHeaders == null) {
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }

        for (final var current : authHeaders) {
            if (current == null || !current.startsWith(BEARER_PREFIX)) {
                continue;
            }

            final var token = current.substring(BEARER_PREFIX_LENGTH);

            // Not ours. Leave it for jwtAuthenticationMechanism — this is the
            // whole reason a prefix is required rather than optional.
            if (!token.startsWith(this.prefix)) {
                continue;
            }

            final var credential = new ApiKeyCredential(token);

            try {
                final var account = this.authenticator.verify(credential);

                if (account != null) {
                    securityContext.authenticationComplete(account, getMechanismName(), false);
                    return AuthenticationMechanismOutcome.AUTHENTICATED;
                }

                // The prefix said this is an API key, so we own the failure.
                // Falling through would gain nothing — it is definitely not a
                // JWT — and would replace a truthful answer with a confusing one.
                LOGGER.debug("API key not verified");
                securityContext.authenticationFailed("API key not verified", getMechanismName());
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            } finally {
                credential.clear();
            }
        }

        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    /**
     * An API key is not an interactive credential, so this must not invite a
     * browser to prompt: no {@code WWW-Authenticate}, and the status code is
     * returned in the result rather than set here, since other mechanisms may
     * still run.
     */
    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        return new ChallengeResult(true, 200);
    }
}
