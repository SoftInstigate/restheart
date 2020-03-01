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

import io.undertow.security.api.AuthenticationMode;
import io.undertow.server.HttpServerExchange;
import java.util.Set;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.TokenManager;
import org.restheart.security.handlers.injectors.TokenInjector;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityHandler extends PipedHttpHandler {
    private final Set<PluginRecord<AuthMechanism>> mechanisms;
    private final Set<PluginRecord<Authorizer>> authorizers;
    private final PluginRecord<TokenManager> tokenManager;

    /**
     *
     * @param mechanisms
     * @param authorizers
     * @param tokenManager
     */
    public SecurityHandler(final Set<PluginRecord<AuthMechanism>> mechanisms,
            final Set<PluginRecord<Authorizer>> authorizers,
            final PluginRecord<TokenManager> tokenManager) {
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
        super.setNext(buildSecurityHandlersChain(next,
                mechanisms, 
                authorizers, 
                tokenManager));
    }

    private static PipelinedHandler buildSecurityHandlersChain(
            PipelinedHandler next,
            final Set<PluginRecord<AuthMechanism>> mechanisms,
            final Set<PluginRecord<Authorizer>> authorizers,
            final PluginRecord<TokenManager> tokenManager) {
        if (mechanisms != null && mechanisms.size() > 0) {
            PipedHttpHandler handler;

            if (authorizers == null || authorizers.isEmpty()) {
                throw new IllegalArgumentException("Error, accessManagers cannot "
                        + "be null or empty. "
                        + "Eventually use FullAccessManager "
                        + "that gives full access power");
            }

            handler = new TokenInjector(
                    new GlobalSecurityPredicatesAuthorizer(authorizers, next),
                    tokenManager.getInstance());

            handler = new SecurityInitialHandler(
                    AuthenticationMode.PRO_ACTIVE,
                    new AuthenticatorMechanismsHandler(
                            new AuthenticationConstraintHandler(
                                    new AuthenticationCallHandler(handler),
                                    authorizers),
                            mechanisms));

            return handler;
        } else {
            return next;
        }
    }
}
