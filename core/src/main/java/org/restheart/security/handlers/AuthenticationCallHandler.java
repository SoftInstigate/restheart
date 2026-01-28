/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import java.io.IOException;

import org.restheart.exchange.Response;
import org.restheart.handlers.CORSHandler;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.RequestInterceptorsExecutor;
import org.restheart.logging.RequestPhaseContext;
import org.restheart.logging.RequestPhaseContext.Phase;
import org.restheart.plugins.InterceptPoint;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationCallHandler.class);

    private final RequestInterceptorsExecutor failedAuthInterceptorsExecutor;

    public AuthenticationCallHandler(final PipelinedHandler next) {
        super(next);
        this.failedAuthInterceptorsExecutor = new RequestInterceptorsExecutor(InterceptPoint.REQUEST_AFTER_FAILED_AUTH);
    }

    /**
     * Only allow the request if successfully authenticated or if
     * authentication is not required.
     *
     * @param exchange
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var sc = exchange.getSecurityContext();

        if (sc.authenticate() && (!sc.isAuthenticationRequired() || sc.isAuthenticated())) {
            // 1 authentication is always attempted
            // 2 requests fails if and only if authentication fails
            //   and authentication is required by all enabled authorizers,
            //   since an authorizer that does not require authentication
            //   might authorize the request even if authentication failed

            if (sc.isAuthenticated()) {
                RequestPhaseContext.setPhase(Phase.PHASE_END);
                LOGGER.debug("AUTHENTICATION COMPLETED - User: {}", sc.getAuthenticatedAccount().getPrincipal().getName());
                RequestPhaseContext.reset();
            } else {
                RequestPhaseContext.setPhase(Phase.PHASE_END);
                LOGGER.debug("AUTHENTICATION COMPLETED - Anonymous");
                RequestPhaseContext.reset();
            }

            if (!exchange.isComplete()) {
                next(exchange);
            }
        } else {
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.debug("AUTHENTICATION FAILED");
            RequestPhaseContext.reset();

            // execute REQUEST_AFTER_FAILED_AUTH interceptors
            failedAuthInterceptorsExecutor.handleRequest(exchange);

            // add CORS headers
            CORSHandler.injectAccessControlAllowHeaders(exchange);
            
            // set status code to 401 Unauthorized if not already set by an interceptor
            // (e.g., an interceptor might set 429 Too Many Requests for rate limiting)
            var response = Response.of(exchange);
            if (response.getStatusCode() < 0) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            }
            
            fastEndExchange(exchange);
        }
    }

    private static final HttpString TRANSFER_ENCODING = HttpString.tryFromString("Transfer-Encoding");

    /**
     * shutdowns the request channel and ends the exchange
     *
     * Closing the request channel prevents delays when handling requests
     * with large data payloads.
     *
     * @param exchange
     * @throws java.io.IOException
     */
    private void fastEndExchange(HttpServerExchange exchange) throws IOException {
        var requestChannel = exchange.getRequestChannel();
        var econding = exchange.getRequestHeaders().get(TRANSFER_ENCODING);

        // shutdown channel only if Transfer-Encoding is not chunked
        // otherwise we get error
        // java.io.IOException: UT000029: Channel was closed mid chunk, if you have attempted to write chunked data you cannot shutdown the channel until after it has all been written
        if (econding == null || !econding.getFirst().startsWith("chunked")) {
            if (requestChannel != null) {
                try {
                    requestChannel.shutdownReads();
                } catch(IOException ie) {
                    LOGGER.debug("ingoring error shutting down reads", ie);
                }
            }
        }

        exchange.endExchange();
    }
}