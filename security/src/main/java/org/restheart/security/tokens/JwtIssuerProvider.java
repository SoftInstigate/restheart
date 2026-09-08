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
package org.restheart.security.tokens;

import java.time.Duration;
import java.util.Map;

import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.JwtIssuer;

/**
 * Publishes the deployment's JWT issuance policy as the injectable {@code jwtIssuer} capability:
 *
 * <pre>
 * &#64;Inject("jwtIssuer")
 * private JwtIssuer jwtIssuer;
 * </pre>
 *
 * <p>The instance is a {@link DefaultJwtIssuer} built from {@link JwtConfigProvider} — the same
 * key, algorithm, issuer, audience and claim policy behind {@code /token} and the login flow — so
 * a plugin minting a token through this provider produces one indistinguishable from, and
 * verifiable exactly like, every other JWT this deployment issues. That is the whole point of it
 * existing: without it, a plugin in a module that cannot see {@code restheart-security} has to
 * carry its own copy of the issuance logic <em>and</em> a second copy of the signing key in its
 * configuration, which then has to be kept in sync by hand.
 *
 * <p>Built lazily on first use rather than in {@code @OnInit}: resolving the password property to
 * deny needs {@code mongoRealmAuthenticator}, which may not be initialized yet while plugins are
 * still being set up — the same reason {@link JwtTokenManager#issuer()} defers it.
 */
@RegisterPlugin(
        name = "jwtIssuer",
        description = "Issues signed JWTs on behalf of other plugins, with the deployment's own signing policy",
        enabledByDefault = true)
public class JwtIssuerProvider implements Provider<JwtIssuer> {
    /** Default lifetime for {@code issue(account)}, in minutes — mirrors {@code jwtTokenManager/ttl}. */
    private static final int DEFAULT_TTL_MINUTES = 15;

    @Inject("config")
    private Map<String, Object> config;

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("jwtConfigProvider")
    private JwtConfigProvider.JwtConfig jwtConfig;

    private volatile JwtIssuer instance;

    @Override
    public JwtIssuer get(PluginRecord<?> caller) {
        var local = this.instance;

        if (local == null) {
            synchronized (this) {
                local = this.instance;

                if (local == null) {
                    local = build();
                    this.instance = local;
                }
            }
        }

        return local;
    }

    private JwtIssuer build() {
        if (jwtConfig == null) {
            throw new ConfigurationException("jwtConfigProvider not available. Ensure it is enabled.");
        }

        int ttlMinutes = argOrDefault(config, "ttl", DEFAULT_TTL_MINUTES);

        return new DefaultJwtIssuer(
                DefaultJwtIssuer.algorithm(jwtConfig.algorithm(), jwtConfig.key()),
                jwtConfig.issuer(),
                jwtConfig.audience(),
                jwtConfig.accountPropertiesClaims(),
                jwtConfig.requiredAccountPropertiesClaims(),
                DefaultJwtIssuer.resolvePasswordProperty(registry),
                Duration.ofMinutes(ttlMinutes));
    }
}
