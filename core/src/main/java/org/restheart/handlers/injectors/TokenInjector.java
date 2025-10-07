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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.security.TokenManager;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class TokenInjector extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenInjector.class);
    
    private final TokenManager tokenManager;

    /**
     * Creates a new instance of TokenInjector
     *
     * @param next
     * @param tokenManager
     */
    public TokenInjector(PipelinedHandler next, TokenManager tokenManager) {
        super(next);
        this.tokenManager = tokenManager;
        
        if (tokenManager != null) {
            var tokenManagerName = PluginUtils.name(tokenManager);
            var tokenManagerClass = tokenManager.getClass().getSimpleName();
            LOGGER.debug("Initialized TokenInjector with token manager: {} ({})", 
                tokenManagerName, tokenManagerClass);
        } else {
            LOGGER.debug("Initialized TokenInjector with no token manager");
        }
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var requestPath = exchange.getRequestPath();
        var requestMethod = exchange.getRequestMethod().toString();
        var securityContext = exchange.getSecurityContext();
        var isAuthenticated = securityContext != null && securityContext.isAuthenticated();
        
        if (this.tokenManager == null) {
            LOGGER.debug("No token manager configured for {} {} - Skipping token injection", 
                requestMethod, requestPath);
        } else if (securityContext == null) {
            LOGGER.debug("No security context for {} {} - Skipping token injection", 
                requestMethod, requestPath);
        } else if (!isAuthenticated) {
            LOGGER.debug("Request not authenticated for {} {} - Skipping token injection", 
                requestMethod, requestPath);
        } else {
            var tokenStartTime = System.currentTimeMillis();
            var authenticatedAccount = securityContext.getAuthenticatedAccount();
            var userPrincipal = authenticatedAccount.getPrincipal().getName();
            var tokenManagerName = PluginUtils.name(tokenManager);
            var tokenManagerClass = tokenManager.getClass().getSimpleName();
            
            LOGGER.debug("Starting token injection for {} {} - User: {} - Token Manager: {} ({})", 
                requestMethod, requestPath, userPrincipal, tokenManagerName, tokenManagerClass);

            try {
                var tokenGenStartTime = System.currentTimeMillis();
                var token = tokenManager.get(authenticatedAccount);
                var tokenGenDuration = System.currentTimeMillis() - tokenGenStartTime;
                
                LOGGER.debug("Token generation completed for user '{}' with {} ({}) in {}ms", 
                    userPrincipal, tokenManagerName, tokenManagerClass, tokenGenDuration);

                var headerInjectionStartTime = System.currentTimeMillis();
                tokenManager.injectTokenHeaders(exchange, token);
                var headerInjectionDuration = System.currentTimeMillis() - headerInjectionStartTime;
                
                var totalTokenDuration = System.currentTimeMillis() - tokenStartTime;
                LOGGER.debug("Token injection completed for {} {} - User: {} - Generation: {}ms, Header injection: {}ms, Total: {}ms", 
                    requestMethod, requestPath, userPrincipal, tokenGenDuration, headerInjectionDuration, totalTokenDuration);
                    
            } catch (Exception ex) {
                var totalTokenDuration = System.currentTimeMillis() - tokenStartTime;
                LOGGER.error("Error during token injection for {} {} - User: {} - Token Manager: {} ({}) after {}ms", 
                    requestMethod, requestPath, userPrincipal, tokenManagerName, tokenManagerClass, totalTokenDuration, ex);
                throw ex;
            }
        }

        next(exchange);
    }
}
