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

import java.util.List;

import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

/**
 * This is the PipedHttpHandler version of
 * io.undertow.security.handlers.AuthenticationMechanismsHandler that adds one
 * or more authentication mechanisms to the security context
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticationMechanismsHandler extends PipedHttpHandler {

    private final List<PluggableAuthenticationMechanism> authenticationMechanisms;

    public AuthenticationMechanismsHandler(final PipedHttpHandler next,
            final List<PluggableAuthenticationMechanism> authenticationMechanisms) {
        super(next);
        this.authenticationMechanisms = authenticationMechanisms;
    }

    public AuthenticationMechanismsHandler(final List<PluggableAuthenticationMechanism> authenticationHandlers) {
        this.authenticationMechanisms = authenticationHandlers;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final SecurityContext sc = exchange.getSecurityContext();

        if (sc != null) {
            authenticationMechanisms.forEach((mechanism) -> {
                sc.addAuthenticationMechanism(new AuthenticationMechanism(mechanism));
            });
        }

        next(exchange);
    }
}
