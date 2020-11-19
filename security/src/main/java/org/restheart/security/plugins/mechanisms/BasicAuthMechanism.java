/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.security.plugins.mechanisms;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import java.util.Map;
import org.restheart.ConfigurationException;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "basicAuthMechanism",
        description = "handles the basic authentication scheme",
        enabledByDefault = false)
public class BasicAuthMechanism extends io.undertow.security.impl.BasicAuthenticationMechanism
        implements AuthMechanism {

    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    public BasicAuthMechanism()
            throws ConfigurationException {
        super("RESTHeart Realm", "basicAuthMechanism", false);
    }

    @InjectConfiguration
    @InjectPluginsRegistry
    public void init(final Map<String, Object> args,
            PluginsRegistry pluginsRegistry)
            throws ConfigurationException {

        // the authenticator specified in auth mechanism configuration
        setIdentityManager(pluginsRegistry
                .getAuthenticator(argValue(args, "authenticator"))
                .getInstance());
    }

    private void setIdentityManager(IdentityManager idm) {
        try {
            var clazz = Class.forName("io.undertow.security.impl.BasicAuthenticationMechanism");
            var idmF = clazz.getDeclaredField("identityManager");
            idmF.setAccessible(true);

            idmF.set(this, idm);
        } catch (ClassNotFoundException
                | SecurityException
                | NoSuchFieldException
                | IllegalAccessException ex) {
            throw new RuntimeException("Error setting identity manager", ex);
        }
    }

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        if (exchange.getRequestHeaders().contains(SILENT_HEADER_KEY)
                || exchange.getQueryParameters().containsKey(SILENT_QUERY_PARAM_KEY)) {
            return new ChallengeResult(true, UNAUTHORIZED);
        } else {
            return super.sendChallenge(exchange, securityContext);
        }
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange,
            final SecurityContext securityContext) {
        return super.authenticate(exchange, securityContext);
    }
}
