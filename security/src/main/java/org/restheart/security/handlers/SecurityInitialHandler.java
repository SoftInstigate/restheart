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
 *//*
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

import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.SecurityContextFactoryImpl;
import io.undertow.server.HttpServerExchange;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.restheart.handlers.PipedHttpHandler;

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
public class SecurityInitialHandler extends PipedHttpHandler {

    private final AuthenticationMode authenticationMode;
    private final String programaticMechName;
    private final SecurityContextFactoryImpl contextFactory;

    static void setSecurityContext(final HttpServerExchange exchange,
            final SecurityContext securityContext) {
        if (System.getSecurityManager() == null) {
            exchange.setSecurityContext(securityContext);
        } else {
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                exchange.setSecurityContext(securityContext);
                return null;
            });
        }
    }

    public SecurityInitialHandler(final AuthenticationMode authenticationMode,
            final String programaticMechName,
            final SecurityContextFactoryImpl contextFactory,
            final PipedHttpHandler next) {
        super(next);
        this.authenticationMode = authenticationMode;
        this.programaticMechName = programaticMechName;
        this.contextFactory = contextFactory;
    }

    public SecurityInitialHandler(final AuthenticationMode authenticationMode,
            final IdentityManager identityManager,
            final String programaticMechName,
            final PipedHttpHandler next) {
        this(authenticationMode,
                programaticMechName,
                (SecurityContextFactoryImpl) SecurityContextFactoryImpl.INSTANCE,
                next);
    }

    public SecurityInitialHandler(final AuthenticationMode authenticationMode,
            final PipedHttpHandler next) {
        this(authenticationMode,
                null,
                (SecurityContextFactoryImpl) SecurityContextFactoryImpl.INSTANCE,
                next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        SecurityContext newContext = this.contextFactory
                .createSecurityContext(exchange, authenticationMode, null,
                        programaticMechName);

        setSecurityContext(exchange, newContext);
        next(exchange);
    }
}
