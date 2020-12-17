/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.security.handlers;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import org.restheart.plugins.security.AuthMechanism;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * log the wapped AuthenticatorMechanism outcomes and makes sure that it can't
 * fail the whole authetication process if it doesn't authenticate the request.
 *
 * when multiple AuthenticatorMechanism are defined, the standard undertow
 * authentication process is:
 *
 * As an in-bound request is received the authenticate method is called on each
 * mechanism in turn until one of the following occurs: <br>
 * - A mechanism successfully authenticates the incoming request. <br>
 * - A mechanism attempts but fails to authenticate the request. <br>
 * - The list of mechanisms is exhausted.
 *
 * * See
 * http://undertow.io/javadoc/2.0.x/io/undertow/security/api/AuthenticationMechanism.html
 *
 * The restheart-security process is:
 *
 * As an in-bound request is received the authenticate method is called on each
 * mechanism in turn until one of the following occurs: <br>
 * - A mechanism successfully authenticates the incoming request. <br>
 * - The list of mechanisms is exhausted. <br>
 *
 * this is achieved avoiding the wrapper AuthenticationMechanism to return
 * NOT_AUTHENTICATED replacing the return value with NOT_ATTEMPTED
 *
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticatorMechanismWrapper implements AuthMechanism {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthMechanism.class);

    private final AuthMechanism wrapped;

    public AuthenticatorMechanismWrapper(AuthMechanism wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange,
            SecurityContext securityContext) {
        AuthenticationMechanismOutcome outcome = wrapped.authenticate(exchange,
                securityContext);

        LOGGER.debug(wrapped.getMechanismName()
                + " -> "
                + outcome.name());

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
