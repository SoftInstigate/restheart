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

import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
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
    private final IdentityManager idm;
    private final String username;
    private final String pwd;

    public IdentityAuthenticationMechanism(IdentityManager idm, String username, String pwd) {
        this.idm = idm;
        this.username = username;
        this.pwd = pwd;
    }

    @Override
    public AuthenticationMechanism.AuthenticationMechanismOutcome authenticate(HttpServerExchange hse, SecurityContext sc) {
        // verify the credentials against the configured IdentityManager
        Account sa = idm.verify(username, new PasswordCredential(pwd.toCharArray()));

        if (sa != null) {
            sc.authenticationComplete(sa,
                    "IdentityAuthenticationManager", false);
            return AuthenticationMechanism.AuthenticationMechanismOutcome.AUTHENTICATED;
        } else {
            // by returning NOT_ATTEMPTED, in case the provided credentials
            // don't match any user of the IdentityManager, the authentication
            // will fallback to the default authentication manager (BasicAuthenticationManager)
            // to make it failing, return NOT_AUTHENTICATED
            return AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }
    }

    @Override
    public AuthenticationMechanism.ChallengeResult sendChallenge(HttpServerExchange hse, SecurityContext sc) {
        return new AuthenticationMechanism.ChallengeResult(true, 200);
    }
}
