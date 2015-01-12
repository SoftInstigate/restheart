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
package org.restheart.handlers;

import org.restheart.security.AccessManager;
import org.restheart.security.handlers.AccessManagerHandler;
import org.restheart.security.handlers.PredicateAuthenticationConstraintHandler;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.List;

/**
 *
 * @author Andrea Di Cesare
 */
public class PipedWrappingHandler extends PipedHttpHandler {

    private final HttpHandler wrapped;

    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param next
     * @param toWrap
     */
    public PipedWrappingHandler(PipedHttpHandler next, HttpHandler toWrap) {
        super(next);
        wrapped = toWrap;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (wrapped == null) {
            next.handleRequest(exchange, context);
        } else {
            wrapped.handleRequest(exchange);

            if (!exchange.isResponseComplete()) {
                next.handleRequest(exchange, context);
            }
        }
    }

    protected static HttpHandler buildSecurityHandlerChain(final AccessManager accessManager, HttpHandler handler, final IdentityManager identityManager, final List<AuthenticationMechanism> mechanisms) {
        if (accessManager != null) {
            handler = new AccessManagerHandler(accessManager, null);
        }
        handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE,
                identityManager,
                new AuthenticationMechanismsHandler(
                        new PredicateAuthenticationConstraintHandler(
                                new AuthenticationCallHandler(handler), accessManager), mechanisms));
        return handler;
    }

}
