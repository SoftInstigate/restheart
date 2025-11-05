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

import java.util.Set;
import java.util.stream.Collectors;

import org.restheart.exchange.Request;
import org.restheart.handlers.CORSHandler;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.logging.RequestPhaseContext;
import org.restheart.logging.RequestPhaseContext.Phase;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;

/**
 * Executes isAllowed() on all enabled authorizer to check the request
 * An Authorizer can be either a VETOER or an ALLOWER
 * A request is allowed when no VETOER denies it and any ALLOWER allows it
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthorizersHandler extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizersHandler.class);
    
    private final Set<PluginRecord<Authorizer>> authorizers;

    /**
     * Creates a new instance of AuthorizersHandler
     *
     * @param authorizers
     * @param next
     */
    public AuthorizersHandler(Set<PluginRecord<Authorizer>> authorizers, PipelinedHandler next) {
        super(next);
        this.authorizers = authorizers;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = Request.of(exchange);
        var requestPath = exchange.getRequestPath();
        var requestMethod = exchange.getRequestMethod().toString();
        var authorizationStartTime = System.currentTimeMillis();
        var isAuthenticated = request.isAuthenticated();
        var userPrincipal = isAuthenticated ? request.getAuthenticatedAccount().getPrincipal().getName() : "anonymous";
        
        RequestPhaseContext.setPhase(Phase.PHASE_START);
        LOGGER.debug("AUTHORIZATION for {} {} - User: {}", requestMethod, requestPath, userPrincipal);

        var isAllowedResult = isAllowed(request);
        var authorizationDuration = System.currentTimeMillis() - authorizationStartTime;
        
        if (isAllowedResult) {
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.debug("✓ ACCESS GRANTED ({}ms)", authorizationDuration);
            RequestPhaseContext.reset();
            next(exchange);
        } else {
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.debug("✗ ACCESS DENIED → 403 Forbidden ({}ms)", authorizationDuration);
            RequestPhaseContext.reset();
                
            // add CORS headers
            CORSHandler.injectAccessControlAllowHeaders(exchange);
            // set status code and end exchange
            exchange.setStatusCode(HttpStatus.SC_FORBIDDEN);
            exchange.endExchange();
        }
    }

    /**
     *
     * @param request
     * @return true if no vetoer vetoes the request and any allower allows it
     */
    @SuppressWarnings("rawtypes")
    private boolean isAllowed(final Request request) {
        var requestPath = request.getPath();
        var requestMethod = request.getMethod().toString();
        var isAuthenticated = request.isAuthenticated();
        var userPrincipal = isAuthenticated ? request.getAuthenticatedAccount().getPrincipal().getName() : "anonymous";
        
        if (authorizers == null || authorizers.isEmpty()) {
            LOGGER.debug("No authorizers configured for {} {} - User: {} - Access DENIED", 
                requestMethod, requestPath, userPrincipal);
            return false;
        }
        
        var authorizersStartTime = System.currentTimeMillis();
        
        // Check VETOER authorizers first
        var vetoers = authorizers.stream()
            .filter(a -> a.isEnabled())
            .filter(a -> a.getInstance() != null)
            .map(a -> a.getInstance())
            .filter(a -> !a.isAuthenticationRequired(request) || request.isAuthenticated())
            .filter(a -> PluginUtils.authorizerType(a) == TYPE.VETOER)
            .collect(Collectors.toList());
            
        if (!vetoers.isEmpty()) {
            RequestPhaseContext.setPhase(Phase.INFO);
            LOGGER.debug("Checking {} VETOER authorizers", vetoers.size());
        }
            
        var vetoerStartTime = System.currentTimeMillis();
        var vetoerResult = true;
        
        for (var vetoer : vetoers) {
            var vetoerName = PluginUtils.name(vetoer);
            var vetoerClass = vetoer.getClass().getSimpleName();
            var vetoerCheckStartTime = System.currentTimeMillis();
            
            try {
                var allowed = vetoer.isAllowed(request);
                var vetoerCheckDuration = System.currentTimeMillis() - vetoerCheckStartTime;
                
                RequestPhaseContext.setPhase(Phase.ITEM);
                LOGGER.debug("VETOER {}: {} ({}ms)", vetoerName, allowed ? "✓" : "✗", vetoerCheckDuration);
                
                if (!allowed) {
                    vetoerResult = false;
                    break;
                }
            } catch (Exception ex) {
                var vetoerCheckDuration = System.currentTimeMillis() - vetoerCheckStartTime;
                LOGGER.error("Error in VETOER {} ({}) for {} {} - User: {} after {}ms", 
                    vetoerName, vetoerClass, requestMethod, requestPath, userPrincipal, vetoerCheckDuration, ex);
                vetoerResult = false;
                break;
            }
        }
        
        var vetoerDuration = System.currentTimeMillis() - vetoerStartTime;
        
        if (!vetoerResult) {
            return false;
        }
        
        
        // Check ALLOWER authorizers
        var allowers = authorizers.stream()
            .filter(a -> a.isEnabled())
            .filter(a -> a.getInstance() != null)
            .map(a -> a.getInstance())
            .filter(a -> PluginUtils.authorizerType(a) == TYPE.ALLOWER)
            .filter(a -> !a.isAuthenticationRequired(request) || request.isAuthenticated())
            .collect(Collectors.toList());
            
        if (!allowers.isEmpty()) {
            RequestPhaseContext.setPhase(Phase.INFO);
            LOGGER.debug("Checking {} ALLOWER authorizers", allowers.size());
        }
            
        var allowerStartTime = System.currentTimeMillis();
        var allowerResult = false;
        
        for (var allower : allowers) {
            var allowerName = PluginUtils.name(allower);
            var allowerClass = allower.getClass().getSimpleName();
            var allowerCheckStartTime = System.currentTimeMillis();
            
            try {
                var allowed = allower.isAllowed(request);
                var allowerCheckDuration = System.currentTimeMillis() - allowerCheckStartTime;
                
                RequestPhaseContext.setPhase(Phase.ITEM);
                LOGGER.debug("ALLOWER {}: {} ({}ms)", allowerName, allowed ? "✓" : "✗", allowerCheckDuration);
                
                if (allowed) {
                    allowerResult = true;
                    break;
                }
            } catch (Exception ex) {
                var allowerCheckDuration = System.currentTimeMillis() - allowerCheckStartTime;
                LOGGER.error("Error in ALLOWER {} ({}) for {} {} - User: {} after {}ms", 
                    allowerName, allowerClass, requestMethod, requestPath, userPrincipal, allowerCheckDuration, ex);
            }
        }
        
        var allowerDuration = System.currentTimeMillis() - allowerStartTime;
        var totalDuration = System.currentTimeMillis() - authorizersStartTime;
        
        
        return vetoerResult && allowerResult;
    }
}
