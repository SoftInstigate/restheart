/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
package org.restheart.security.handlers;

import io.undertow.security.api.AuthenticationMode;
import io.undertow.server.HttpServerExchange;
import java.util.Set;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.injectors.TokenInjector;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.TokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityHandler extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityHandler.class);

    private final Set<PluginRecord<AuthMechanism>> mechanisms;
    private final Set<PluginRecord<Authorizer>> authorizers;
    private final PluginRecord<TokenManager> tokenManager;

    // Cached security handler chain components (created once, reused across all services)
    private static volatile SecurityChainComponents cachedComponents = null;
    private static final Object LOCK = new Object();

    // Helper class to hold the reusable chain components
    private static class SecurityChainComponents {
        final ReusableAuthenticatorMechanismsHandler authMechanismsHandler;
        final ReusableTokenInjector tokenInjector;
        final ReusableAuthorizersHandler authorizersHandler;
        final ReusableAuthenticationConstraintHandler constraintHandler;
        final ReusableAuthenticationCallHandler callHandler;

        SecurityChainComponents(
                Set<PluginRecord<AuthMechanism>> mechanisms,
                Set<PluginRecord<Authorizer>> authorizers,
                TokenManager tokenManager) {
            var startTime = System.currentTimeMillis();
            LOGGER.debug("┌── SECURITY HANDLERS INITIALIZATION");

            // Build the reusable components (without linking them yet)
            this.authorizersHandler = new ReusableAuthorizersHandler(authorizers);
            this.tokenInjector = new ReusableTokenInjector(tokenManager);
            this.callHandler = new ReusableAuthenticationCallHandler();
            this.constraintHandler = new ReusableAuthenticationConstraintHandler(authorizers);
            this.authMechanismsHandler = new ReusableAuthenticatorMechanismsHandler(mechanisms);

            var duration = System.currentTimeMillis() - startTime;
            LOGGER.debug("└── SECURITY HANDLERS INITIALIZED in {}ms", duration);
        }

        void linkChain(PipelinedHandler next) {
            authorizersHandler.setNext(next);
            tokenInjector.setNext(authorizersHandler);
            callHandler.setNext(tokenInjector);
            constraintHandler.setNext(callHandler);
            authMechanismsHandler.setNext(constraintHandler);
        }
    }

    // Wrapper classes that expose setNext() publicly
    private static class ReusableAuthenticatorMechanismsHandler extends AuthenticatorMechanismsHandler {
        ReusableAuthenticatorMechanismsHandler(Set<PluginRecord<AuthMechanism>> mechanisms) {
            super(mechanisms);
        }

        @Override
        public void setNext(PipelinedHandler next) {
            super.setNext(next);
        }
    }

    private static class ReusableTokenInjector extends TokenInjector {
        ReusableTokenInjector(TokenManager tokenManager) {
            super(null, tokenManager);
        }

        @Override
        public void setNext(PipelinedHandler next) {
            super.setNext(next);
        }
    }

    private static class ReusableAuthorizersHandler extends AuthorizersHandler {
        ReusableAuthorizersHandler(Set<PluginRecord<Authorizer>> authorizers) {
            super(authorizers, null);
        }

        @Override
        public void setNext(PipelinedHandler next) {
            super.setNext(next);
        }
    }

    private static class ReusableAuthenticationCallHandler extends AuthenticationCallHandler {
        ReusableAuthenticationCallHandler() {
            super(null);
        }

        @Override
        public void setNext(PipelinedHandler next) {
            super.setNext(next);
        }
    }

    private static class ReusableAuthenticationConstraintHandler extends AuthenticationConstraintHandler {
        ReusableAuthenticationConstraintHandler(Set<PluginRecord<Authorizer>> authorizers) {
            super(null, authorizers);
        }

        @Override
        public void setNext(PipelinedHandler next) {
            super.setNext(next);
        }
    }

    /**
     *
     * @param mechanisms
     * @param authorizers
     * @param tokenManager
     */
    public SecurityHandler(final Set<PluginRecord<AuthMechanism>> mechanisms, final Set<PluginRecord<Authorizer>> authorizers, final PluginRecord<TokenManager> tokenManager) {
        super();

        this.mechanisms = mechanisms;
        this.authorizers = authorizers;
        this.tokenManager = tokenManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        next(exchange);
    }

    @Override
    protected void setNext(PipelinedHandler next) {
        super.setNext(buildSecurityHandlersChain(next, mechanisms, authorizers, tokenManager));
    }

    /**
     * Pre-initialize security handlers (normally they are lazily initialized on first use)
     * This is useful to show initialization logs before service binding
     *
     * @param mechanisms
     * @param authorizers
     * @param tokenManager
     */
    public static void preInitialize(
            final Set<PluginRecord<AuthMechanism>> mechanisms,
            final Set<PluginRecord<Authorizer>> authorizers,
            final PluginRecord<TokenManager> tokenManager) {
        if (mechanisms != null && !mechanisms.isEmpty()) {
            if (cachedComponents == null) {
                synchronized (LOCK) {
                    if (cachedComponents == null) {
                        cachedComponents = new SecurityChainComponents(
                            mechanisms,
                            authorizers,
                            tokenManager != null ? tokenManager.getInstance() : null
                        );
                    }
                }
            }
        }
    }

    private static PipelinedHandler buildSecurityHandlersChain(
            PipelinedHandler next,
            final Set<PluginRecord<AuthMechanism>> mechanisms,
            final Set<PluginRecord<Authorizer>> authorizers,
            final PluginRecord<TokenManager> tokenManager) {
        if (authorizers == null || authorizers.isEmpty()) {
            throw new IllegalArgumentException("Error, authorizers cannot "
                    + "be null or empty. "
                    + "Eventually use FullAuthorizer "
                    + "that gives full access power");
        }

        if (mechanisms != null && !mechanisms.isEmpty()) {
            // Pre-initialize for logging (only once)
            if (cachedComponents == null) {
                synchronized (LOCK) {
                    if (cachedComponents == null) {
                        cachedComponents = new SecurityChainComponents(
                            mechanisms,
                            authorizers,
                            tokenManager != null ? tokenManager.getInstance() : null
                        );
                    }
                }
            }

            // Create NEW handler instances for each service (cannot reuse because of 'next' pointer)
            var authorizersHandler = new ReusableAuthorizersHandler(authorizers);
            var tokenInjector = new ReusableTokenInjector(tokenManager != null ? tokenManager.getInstance() : null);
            var callHandler = new ReusableAuthenticationCallHandler();
            var constraintHandler = new ReusableAuthenticationConstraintHandler(authorizers);
            var authMechanismsHandler = new ReusableAuthenticatorMechanismsHandler(mechanisms);

            // Link the chain
            authorizersHandler.setNext(next);
            tokenInjector.setNext(authorizersHandler);
            callHandler.setNext(tokenInjector);
            constraintHandler.setNext(callHandler);
            authMechanismsHandler.setNext(constraintHandler);

            return new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, authMechanismsHandler);

        } else if (authorizers != null && !authorizers.isEmpty()) {
            // if no authentication mechanism is enabled and at least one authorizer is defined
            // just pipe the authorizers
            // this will make the request to be authorized without any authentication mechanism
            // see https://github.com/SoftInstigate/restheart/discussions/417
            return new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, new AuthorizersHandler(authorizers, next));
        } else {
            return next;
        }
    }
}
