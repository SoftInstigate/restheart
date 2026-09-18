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

import org.restheart.exchange.Exchange;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.utils.InProcessDispatcher;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

/**
 * Signs in a request that RESTHeart dispatched to itself in-process, with the identity the
 * dispatcher attached to it: the MCP {@code call_api} tool runs an action of a resource this way,
 * as the MCP session's own account, without minting any credential.
 *
 * <p>Both conditions must hold, and both can only be established by
 * {@link InProcessDispatcher}'s own root handler, never from the wire: the exchange carries the
 * {@link InProcessDispatcher#IN_PROCESS} marker, and an {@link InProcessDispatcher#PRINCIPAL}
 * account. Any other request is not attempted, so this mechanism is inert for every request
 * that arrives on a listener.
 */
@RegisterPlugin(
        name = "inProcessAuthMechanism",
        description = "authenticates a request dispatched in-process with the identity attached by the dispatcher",
        enabledByDefault = true)
public class InProcessAuthMechanism implements AuthMechanism {
    private static final String MECHANISM_NAME = "InProcessAuthMechanism";

    @Override
    public AuthenticationMechanism.AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        if (!Exchange.isInProcess(exchange)) {
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }

        var principal = exchange.getAttachment(InProcessDispatcher.PRINCIPAL);

        if (principal == null) {
            // an in-process request with no identity: let the other mechanisms have their say,
            // exactly as for an anonymous request from a listener
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }

        securityContext.authenticationComplete(principal, MECHANISM_NAME, false);
        return AuthenticationMechanismOutcome.AUTHENTICATED;
    }

    @Override
    public AuthenticationMechanism.ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        // never challenges: a request this mechanism did not sign in is somebody else's to challenge
        return new AuthenticationMechanism.ChallengeResult(false);
    }
}
