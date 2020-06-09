/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 SoftInstigate
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
package org.restheart.test.plugins.initializers;

import com.auth0.jwt.exceptions.JWTVerificationException;
import org.restheart.ConfigurationException;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.plugins.mechanisms.JwtAuthenticationMechanism;

/**
 * Demonstrate how to add an extra verification step to the
 * jwtAuthenticationMechanism.
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "extraJwtVerifier",
        priority = 100,
        description = "Adds an extra verifictation step "
        + "to the jwtAuthenticationMechanism")
public class ExtraJwtVerifier implements Initializer {
    private PluginsRegistry registry;

    @InjectPluginsRegistry
    public void init(PluginsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void init() {
        JwtAuthenticationMechanism am;
        try {
            var pr = registry
                    .getAuthMechanisms()
                    .stream()
                    .filter(_am -> "jwtAuthenticationMechanism".equals(_am.getName()))
                    .findFirst();

            if (pr.isPresent()) {
                am = (JwtAuthenticationMechanism) pr.get().getInstance();
            } else {
                throw new IllegalStateException("cannot get jwtAuthenticationMechanism");
            }
        } catch (ConfigurationException | ClassCastException ex) {
            throw new IllegalStateException("cannot get jwtAuthenticationMechanism", ex);
        }

        am.setExtraJwtVerifier(token -> {
            var extraClaim = token.getClaim("extra");

            if (extraClaim == null || extraClaim.isNull()) {
                throw new JWTVerificationException("missing extra claim");
            }

            var extra = extraClaim.asMap();

            if (!extra.containsKey("a")) {
                throw new JWTVerificationException("extra claim does not have "
                        + "'a' property");
            }

            if (!extra.containsKey("b")) {
                throw new JWTVerificationException("extra claim does not have "
                        + "'b' property");
            }
        });
    }
}
