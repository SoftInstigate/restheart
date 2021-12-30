/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.CORSHandler;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.utils.HttpStatus;

/**
 * This is the PipelinedHandler version of
 * io.undertow.security.handlers.AuthenticationCallHandler that is the final
 * {@link HttpHandler} in the security chain, it's purpose is to act as a
 * barrier at the end of the chain to ensure authenticate is called after the
 * mechanisms have been associated with the context and the constraint checked.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticationCallHandler extends PipelinedHandler {

    public AuthenticationCallHandler(final PipelinedHandler next) {
        super(next);
    }

    /**
     * Only allow the request through if successfully authenticated or if
     * authentication is not required.
     *
     * @throws java.lang.Exception
     * See
     * io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        var sc = exchange.getSecurityContext();

        // 1 authentication is always attempted
        // 2 requests fails if and only if authentication fails
        //   and authentication is required by all enabled authorizers:
        //   since an authorizer that does not require authentication
        //   might authorize the request even if authentication failed
        if (sc.authenticate() && (!sc.isAuthenticationRequired() || sc.isAuthenticated())) {
            if (!exchange.isComplete()) {
                next(exchange);
            }
        } else {
            // add CORS headers
            CORSHandler.injectAccessControlAllowHeaders(exchange);
            // set status code and end exchange
            exchange.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            exchange.endExchange();
        }
    }
}
