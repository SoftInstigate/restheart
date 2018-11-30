/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
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
package io.uiam.handlers.security;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.uiam.Bootstrapper;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;
import static io.uiam.handlers.security.IAuthToken.AUTH_TOKEN_HEADER;
import static io.uiam.handlers.security.IAuthToken.AUTH_TOKEN_VALID_HEADER;
import io.uiam.plugins.IDMCacheSingleton;
import io.uiam.plugins.authentication.impl.AuthTokenIdentityManager;
import io.uiam.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthTokenHandler extends PipedHttpHandler {

    private static final boolean ENABLED = Bootstrapper.getConfiguration().isAuthTokenEnabled();

    // used to compare the requested URI containing escaped chars
    private static final Escaper ESCAPER = UrlEscapers.urlPathSegmentEscaper();

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

        if (exchange.getRequestPath().startsWith("/_authtokens/")
                && exchange.getRequestPath().length() > 13
                && Methods.OPTIONS.equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders()
                    .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, DELETE")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");

            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.endExchange();
            return;
        }

        if (exchange.getSecurityContext() == null
                || exchange.getSecurityContext().getAuthenticatedAccount() == null
                || exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal() == null
                || !(("/_authtokens/" + exchange.getSecurityContext()
                        .getAuthenticatedAccount().getPrincipal().getName())
                        .equals(exchange.getRequestURI())
                || !(ESCAPER.escape("/_authtokens/" + exchange.getSecurityContext()
                        .getAuthenticatedAccount().getPrincipal().getName()))
                        .equals(exchange.getRequestURI()))) {
            exchange.setStatusCode(HttpStatus.SC_FORBIDDEN);
            exchange.endExchange();
            return;
        }

        if (Methods.GET.equals(exchange.getRequestMethod())) {
            JsonObject resp = new JsonObject();

            resp.add("auth_token",
                    new JsonPrimitive(exchange.getResponseHeaders()
                            .get(AUTH_TOKEN_HEADER).getFirst()));

            resp.add("auth_token_valid_until",
                    new JsonPrimitive(exchange.getResponseHeaders()
                            .get(AUTH_TOKEN_VALID_HEADER).getFirst()));

            exchange.setStatusCode(HttpStatus.SC_OK);
            //TODO use static var for content type
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(resp.toString());
            exchange.endExchange();
        } else if (Methods.DELETE.equals(exchange.getRequestMethod())) {
            ((AuthTokenIdentityManager) IDMCacheSingleton.getInstance()
                    .getIdentityManager(AuthTokenIdentityManager.NAME))
                    .getCachedAccounts()
                    .invalidate(exchange
                            .getSecurityContext()
                            .getAuthenticatedAccount()
                            .getPrincipal()
                            .getName());
            removeAuthTokens(exchange);
            exchange.setStatusCode(HttpStatus.SC_NO_CONTENT);
            exchange.endExchange();
        } else {
            exchange.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }

    private void removeAuthTokens(HttpServerExchange exchange) {
        exchange.getResponseHeaders().remove(AUTH_TOKEN_HEADER);
        exchange.getResponseHeaders().remove(AUTH_TOKEN_VALID_HEADER);
    }
}
