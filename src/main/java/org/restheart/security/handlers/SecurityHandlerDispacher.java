/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.security.handlers;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.security.AccessManager;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityHandlerDispacher extends PipedHttpHandler {

    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    private final SecurityHandler silentHandler;
    private final SecurityHandler challengingHandler;

    /**
     *
     * @param next
     * @param authenticationMechanism
     * @param identityManager
     * @param accessManager
     */
    public SecurityHandlerDispacher(final PipedHttpHandler next, final AuthenticationMechanism authenticationMechanism, final IdentityManager identityManager, final AccessManager accessManager) {
        super(null);

        silentHandler = new SecurityHandler(next, authenticationMechanism, identityManager, accessManager, false);
        challengingHandler = new SecurityHandler(next, authenticationMechanism, identityManager, accessManager, true);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (exchange.getRequestHeaders().contains(SILENT_HEADER_KEY) || exchange.getQueryParameters().containsKey(SILENT_QUERY_PARAM_KEY)) {
            silentHandler.handleRequest(exchange, context);
        } else {
            challengingHandler.handleRequest(exchange, context);
        }
    }
}
