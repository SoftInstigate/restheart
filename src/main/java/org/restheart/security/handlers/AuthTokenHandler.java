/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.security.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.restheart.Bootstrapper;
import org.restheart.hal.Representation;
import static org.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_VALID_HEADER;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.security.impl.AuthTokenIdentityManager;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class AuthTokenHandler extends PipedHttpHandler {

    private static final boolean ENABLED = Bootstrapper.getConfiguration().isAuthTokenEnabled();

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (!ENABLED) {
            return;
        }

        if (exchange.getSecurityContext() == null
                || exchange.getSecurityContext().getAuthenticatedAccount() == null
                || exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal() == null
                || !("/_authtokens/" + exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName())
                        .equals(exchange.getRequestURI())) {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_FORBIDDEN);
            return;
        }

        if (Methods.GET.equals(exchange.getRequestMethod())) {
            Representation rep = new Representation("/_authtokens/" 
                    + exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName());

            rep.addProperty("auth_token", exchange.getResponseHeaders().get(AUTH_TOKEN_HEADER).getFirst());
            rep.addProperty("auth_token_valid_until", exchange.getResponseHeaders().get(AUTH_TOKEN_VALID_HEADER).getFirst());

            exchange.setResponseCode(HttpStatus.SC_OK);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
            exchange.getResponseSender().send(rep.toString());
            exchange.endExchange();
        } else if (Methods.DELETE.equals(exchange.getRequestMethod())) {
            AuthTokenIdentityManager.getInstance().getCachedAccounts()
                    .invalidate(exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName());
            removeAuthTokens(exchange);
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NO_CONTENT);
        } else {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void removeAuthTokens(HttpServerExchange exchange) {
        exchange.getResponseHeaders().remove(AUTH_TOKEN_HEADER);
        exchange.getResponseHeaders().remove(AUTH_TOKEN_VALID_HEADER);
    }
}
