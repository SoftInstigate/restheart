/*
 * Copyright 2014 - 2015 SoftInstigate.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
