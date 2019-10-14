/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.handlers;

import org.restheart.security.handlers.injectors.TokenInjector;
import java.util.List;

import io.undertow.security.api.AuthenticationMode;
import io.undertow.server.HttpServerExchange;
import java.util.LinkedHashSet;
import org.restheart.security.plugins.TokenManager;
import org.restheart.security.plugins.Authorizer;
import org.restheart.security.plugins.AuthMechanism;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityHandler extends PipedHttpHandler {
    private final List<AuthMechanism> authenticationMechanisms;
    private final LinkedHashSet<Authorizer> accessManagers;
    private final TokenManager tokenManager;

    /**
     *
     * @param authenticationMechanisms
     * @param accessManagers
     */
    public SecurityHandler(final List<AuthMechanism> authenticationMechanisms,
            final LinkedHashSet<Authorizer> accessManagers,
            final TokenManager tokenManager) {
        super();

        this.authenticationMechanisms = authenticationMechanisms;
        this.accessManagers = accessManagers;        
        this.tokenManager = tokenManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        next(exchange);
    }

    @Override
    protected void setNext(PipedHttpHandler next) {
        super.setNext(buildSecurityHandlersChain(next,
                authenticationMechanisms, 
                accessManagers, 
                tokenManager));
    }

    private static PipedHttpHandler buildSecurityHandlersChain(
            PipedHttpHandler next,
            final List<AuthMechanism> mechanisms,
            final LinkedHashSet<Authorizer> accessManagers,
            final TokenManager tokenManager) {
        if (mechanisms != null && mechanisms.size() > 0) {
            PipedHttpHandler handler;

            if (accessManagers == null || accessManagers.isEmpty()) {
                throw new IllegalArgumentException("Error, accessManagers cannot "
                        + "be null or empty. "
                        + "Eventually use FullAccessManager "
                        + "that gives full access power");
            }

            handler = new TokenInjector(
                    new GlobalSecurityPredicatesAuthorizer(accessManagers, next),
                    tokenManager);

            handler = new SecurityInitialHandler(
                    AuthenticationMode.PRO_ACTIVE,
                    new AuthenticatorMechanismsHandler(
                            new AuthenticationConstraintHandler(
                                    new AuthenticationCallHandler(handler),
                                    accessManagers),
                            mechanisms));

            return handler;
        } else {
            return next;
        }
    }
}
