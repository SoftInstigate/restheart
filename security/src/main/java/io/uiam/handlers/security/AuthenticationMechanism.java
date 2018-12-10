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
package io.uiam.handlers.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

/**
 * log PluggableAuthenticationMechanism outcomes and makes sure that the wrapped
 * PluggableAuthenticationMechanism can't fail the whole authetication process
 * if it doesn't authenticate the request.
 *
 * when multiple AuthenticationMechanisms are defined, the standard undertow
 * authentication process is:
 *
 * As an in-bound request is received the authenticate method is called on each
 * mechanism in turn until one of the following occurs: - A mechanism
 * successfully authenticates the incoming request. - A mechanism attempts but
 * fails to authenticate the request. - The list of mechanisms is exhausted.
 *
 * * @see
 * http://undertow.io/javadoc/2.0.x/io/undertow/security/api/AuthenticationMechanism.html
 *
 * The uIAM process is:
 *
 * As an in-bound request is received the authenticate method is called on each
 * mechanism in turn until one of the following occurs: - A mechanism
 * successfully authenticates the incoming request. - The list of mechanisms is
 * exhausted.
 *
 * this is achieved avoiding the wrapper AuthenticationMechanism to return
 * NOT_AUTHENTICATED replacing the return value to NOT_ATTEMPTED
 *
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class AuthenticationMechanism
        implements PluggableAuthenticationMechanism {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(AuthenticationMechanism.class);

    private final PluggableAuthenticationMechanism wrapped;

    public AuthenticationMechanism(PluggableAuthenticationMechanism wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange,
            SecurityContext securityContext) {
        AuthenticationMechanismOutcome outcome = wrapped
                .authenticate(exchange, securityContext);

        LOGGER.debug(wrapped.getMechanismName() + " -> " + outcome.name());

        switch (outcome) {
            case NOT_AUTHENTICATED:
                return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
            default:
                return outcome;
        }
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange,
            SecurityContext securityContext) {
        return wrapped.sendChallenge(exchange, securityContext);
    }

    @Override
    public String getMechanismName() {
        return wrapped.getMechanismName();
    }
}
