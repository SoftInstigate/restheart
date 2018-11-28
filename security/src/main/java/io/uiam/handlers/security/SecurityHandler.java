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

import io.undertow.server.HttpServerExchange;
import java.util.List;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.uiam.plugins.authentication.PluggableIdentityManager;
import io.uiam.plugins.authorization.PluggableAccessManager;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityHandler extends PipedHttpHandler {

    private static PipedHttpHandler getSecurityHandlerChain(final PipedHttpHandler next, 
            final List<PluggableAuthenticationMechanism> authenticationMechanisms,
            final PluggableIdentityManager identityManager, 
            final PluggableAccessManager accessManager) {
        if (identityManager != null) {
            return buildSecurityHandlerChain(next, 
                    authenticationMechanisms, 
                    accessManager, 
                    identityManager);
        } else {
            return next;
        }
    }

    /**
     *
     * @param next
     * @param authenticationMechanisms
     * @param identityManager
     * @param accessManager
     */
    public SecurityHandler(final PipedHttpHandler next, 
            final List<PluggableAuthenticationMechanism> authenticationMechanisms,
            final PluggableIdentityManager identityManager, 
            final PluggableAccessManager accessManager) {
        
        super(getSecurityHandlerChain(next, 
                authenticationMechanisms, 
                identityManager, 
                accessManager));
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        next(exchange, context);
    }

}
