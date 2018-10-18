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
package io.uiam.security.handlers;

import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.SecurityContextFactoryImpl;
import io.undertow.server.HttpServerExchange;
import java.security.AccessController;
import java.security.PrivilegedAction;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;

/**
 * This is the PipedHttpHandler version of
 * io.undertow.security.handlers.SecurityInitialHandler the security handler
 * responsible for attaching the SecurityContext to the current
 * {@link HttpServerExchange}.
 *
 * This handler is called early in the processing of the incoming request,
 * subsequently supported authentication mechanisms will be added to the
 * context, a decision will then be made if authentication is required or
 * optional and the associated mechanisms will be called.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@SuppressWarnings("deprecation")
public class SecurityInitialHandler extends PipedHttpHandler {

    static void setSecurityContext(final HttpServerExchange exchange, final SecurityContext securityContext) {
        if (System.getSecurityManager() == null) {
            exchange.setSecurityContext(securityContext);
        } else {
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                exchange.setSecurityContext(securityContext);
                return null;
            });
        }
    }

    private final AuthenticationMode authenticationMode;
    private final IdentityManager identityManager;
    private final String programaticMechName;
    private final io.undertow.security.api.SecurityContextFactory contextFactory;

    public SecurityInitialHandler(final AuthenticationMode authenticationMode, final IdentityManager identityManager,
            final String programaticMechName,
            final io.undertow.security.api.SecurityContextFactory contextFactory,
            final PipedHttpHandler next) {
        super(next);
        this.authenticationMode = authenticationMode;
        this.identityManager = identityManager;
        this.programaticMechName = programaticMechName;
        this.contextFactory = contextFactory;
    }

    public SecurityInitialHandler(final AuthenticationMode authenticationMode, final IdentityManager identityManager,
            final String programaticMechName, final PipedHttpHandler next) {
        this(authenticationMode,
                identityManager,
                programaticMechName,
                SecurityContextFactoryImpl.INSTANCE,
                next);
    }

    public SecurityInitialHandler(final AuthenticationMode authenticationMode, final IdentityManager identityManager,
            final PipedHttpHandler next) {
        this(authenticationMode, identityManager, null, SecurityContextFactoryImpl.INSTANCE, next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        SecurityContext newContext = this.contextFactory
                .createSecurityContext(exchange,
                        authenticationMode,
                        identityManager,
                        programaticMechName);

        setSecurityContext(exchange, newContext);
        next(exchange, context);
    }

}
