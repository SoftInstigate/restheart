/*
 * uIAM - the IAM for microservices
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
