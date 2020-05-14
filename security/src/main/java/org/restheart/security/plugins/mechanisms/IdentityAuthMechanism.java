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

import com.google.common.collect.Sets;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.Map;
import org.restheart.ConfigurationException;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.BaseAccount;

/**
 * a simple Auth Mechanism to demonstrate how to plug a custom
 * AuthenticationMechanism
 *
 * it authenticates all requests against the configured IdentityManager using
 * the credentials specified in the configuration file
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "identityAuthMechanism",
        description = "authenticates all requests against the configured "
                + "IdentityManager using the credentials specified "
                + "in the configuration file",
        enabledByDefault = false)
public class IdentityAuthMechanism implements AuthMechanism {
    private String username;
    private List<String> roles;

    @InjectConfiguration
    public void init(Map<String, Object> confArgs)
            throws ConfigurationException {
        this.username = argValue(confArgs, "username");
        this.roles = argValue(confArgs, "roles");
    }

    @Override
    public AuthenticationMechanism.AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange,
            SecurityContext securityContext) {
        Account sa = new BaseAccount(username, Sets.newTreeSet(roles));

        securityContext.authenticationComplete(sa, "IdentityAuthenticationManager", true);
        return AuthenticationMechanism.AuthenticationMechanismOutcome.AUTHENTICATED;
    }

    @Override
    public AuthenticationMechanism.ChallengeResult sendChallenge(HttpServerExchange exchange,
            SecurityContext securityContext) {
        return new AuthenticationMechanism.ChallengeResult(true, 200);
    }
}
