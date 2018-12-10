/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.plugins.authentication.impl;

import static io.uiam.plugins.ConfigurablePlugin.argValue;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Sets;

import io.uiam.plugins.PluginConfigurationException;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;

/**
 * a simple IdentityAuthenticationMechanism to demonstrate how to plug a custom
 * AuthenticationMechanism
 *
 * it authenticates all requests against the configured IdentityManager using
 * the credentials specified in the configuration file
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class IdentityAuthenticationMechanism implements PluggableAuthenticationMechanism {
    private final String mechanismName;
    private final String username;
    private final List<String> roles;

    public IdentityAuthenticationMechanism(String mechanismName, Map<String, Object> args)
            throws PluginConfigurationException {
        this.mechanismName = mechanismName;
        this.username = argValue(args, "username");
        this.roles = argValue(args, "roles");

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

    /**
     * @return the mechanismName
     */
    public String getMechanismName() {
        return mechanismName;
    }
}
