/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityHandler extends PipelinedHandler {
    private final Set<PluginRecord<AuthMechanism>> mechanisms;
    private final Set<PluginRecord<Authorizer>> authorizers;
    private final PluginRecord<TokenManager> tokenManager;

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

        if (mechanisms != null && mechanisms.size() > 0) {
            return new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE,
                new AuthenticatorMechanismsHandler(
                    new AuthenticationConstraintHandler(
                        new AuthenticationCallHandler(
                            new TokenInjector(
                                new AuthorizersHandler(authorizers, next),
                            tokenManager != null ? tokenManager.getInstance() : null)),
                        authorizers),
                mechanisms));

        } else if (authorizers != null && authorizers.size() > 0) {
            // if no authentication mechanism is enabled and at least one authorizer is defined
            // just pipe the autorizers
            // this will make the request to be authorized without any authentication mechanism
            // see https://github.com/SoftInstigate/restheart/discussions/417
            return new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, new AuthorizersHandler(authorizers, next));
        } else {
            return next;
        }
    }
}
