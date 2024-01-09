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

import io.undertow.server.HttpServerExchange;
import java.util.Set;
import org.restheart.exchange.Request;
import org.restheart.handlers.CORSHandler;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.PluginUtils;

/**
 * Executes isAllowed() on all enabled authorizer to check the request
 * An Authorizer can be either a VETOER or an ALLOWER
 * A request is allowed when no VETOER denies it and any ALLOWER allows it
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthorizersHandler extends PipelinedHandler {
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

        if (isAllowed(request)) {
            next(exchange);
        } else {
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
        if (authorizers == null || authorizers.isEmpty()) {
            return false;
        } else {
            return authorizers.stream()
                // at least one ALLOWER must authorize it
                .filter(a -> a.isEnabled())
                .filter(a -> a.getInstance() != null)
                .map(a -> a.getInstance())
                .filter(a -> PluginUtils.authorizerType(a) == TYPE.ALLOWER)
                // filter out authorizers that requires authentication when the request is not authenticated
                .filter(a -> !a.isAuthenticationRequired(request) || request.isAuthenticated())
                .anyMatch(a -> a.isAllowed(request))
                // all VETOERs must authorize it
                && authorizers.stream()
                .filter(a -> a.isEnabled())
                .filter(a -> a.getInstance() != null)
                .map(a -> a.getInstance())
                // filter out authorizers that requires authentication when the request is not authenticated
                .filter(a -> !a.isAuthenticationRequired(request) || request.isAuthenticated())
                .filter(a -> PluginUtils.authorizerType(a) == TYPE.VETOER)
                .allMatch(a -> a.isAllowed(request));
        }
    }
}
