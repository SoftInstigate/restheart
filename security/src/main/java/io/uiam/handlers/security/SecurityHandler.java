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
import io.uiam.handlers.RequestContext;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.uiam.plugins.authorization.PluggableAccessManager;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityHandler extends PipedHttpHandler {

    /**
     *
     * @param next
     * @param authenticationMechanisms
     * @param accessManager
     */
    public SecurityHandler(final PipedHttpHandler next,
            final List<PluggableAuthenticationMechanism> authenticationMechanisms,
            final PluggableAccessManager accessManager) {

        super(buildSecurityHandlersChain(next, authenticationMechanisms, accessManager));
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        next(exchange, context);
    }

    private static PipedHttpHandler buildSecurityHandlersChain(PipedHttpHandler next,
            final List<PluggableAuthenticationMechanism> mechanisms, final PluggableAccessManager accessManager) {
        if (mechanisms != null && mechanisms.size() > 0) {
            PipedHttpHandler handler;

            if (accessManager == null) {
                throw new IllegalArgumentException("Error, accessManager cannot " + "be null. "
                        + "Eventually use FullAccessManager " + "that gives full access power ");
            }

            handler = new AuthTokenInjecterHandler(new AccessManagerHandler(accessManager, next));

            handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE,
                    new AuthenticationMechanismsHandler(
                            new AuthenticationConstraintHandler(new AuthenticationCallHandler(handler), accessManager),
                            mechanisms));

            return handler;
        } else {
            return next;
        }
    }

}
