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

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
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
     * @see
     * io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        SecurityContext rcontext = exchange.getSecurityContext();

        // 1 call authenticate that performs authentication on the request. 
        // 2 make sure that, only if authentication is required, than the request is authenticated
        if (rcontext.authenticate()
                && (!rcontext.isAuthenticationRequired()
                || rcontext.isAuthenticated())) {
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
