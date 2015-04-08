/*
 * RESTHeart - the data REST API server
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
package org.restheart.security.handlers;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.util.List;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;

/**
 * This is the PipedHttpHandler version of io.undertow.security.handlers.AuthenticationMechanismsHandler
 * that adds one or more authentication
 * mechanisms to the security context
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class AuthenticationMechanismsHandler extends PipedHttpHandler {
    private final List<AuthenticationMechanism> authenticationMechanisms;

    public AuthenticationMechanismsHandler(final PipedHttpHandler next, final List<AuthenticationMechanism> authenticationMechanisms) {
        super(next);
        this.authenticationMechanisms = authenticationMechanisms;
    }

    public AuthenticationMechanismsHandler(final List<AuthenticationMechanism> authenticationHandlers) {
        this.authenticationMechanisms = authenticationHandlers;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        final SecurityContext sc = exchange.getSecurityContext();
        if(sc != null) {
            for(AuthenticationMechanism mechanism : authenticationMechanisms) {
                sc.addAuthenticationMechanism(mechanism);
            }
        }
        getNext().handleRequest(exchange, context);
    }
}

