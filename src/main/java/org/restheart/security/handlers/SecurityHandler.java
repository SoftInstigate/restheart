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
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.List;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.security.AccessManager;
import org.restheart.security.AuthTokenAuthenticationMechanism;
import static org.restheart.security.RestheartIdentityManager.RESTHEART_REALM;
import org.restheart.security.SilentBasicAuthenticationMechanism;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityHandler extends PipedHttpHandler {

    private static PipedHttpHandler getSecurityHandlerChain(final PipedHttpHandler next, AuthenticationMechanism authenticationMechanism, final IdentityManager identityManager, final AccessManager accessManager, final boolean challenging) {
        if (identityManager != null) {
            final List<AuthenticationMechanism> mechanisms = new ArrayList<>();

            if (authenticationMechanism != null) {
                mechanisms.add(authenticationMechanism);
            }

            mechanisms.add(new AuthTokenAuthenticationMechanism(RESTHEART_REALM));

            if (challenging) {
                mechanisms.add(new BasicAuthenticationMechanism(RESTHEART_REALM));
            } else {
                mechanisms.add(new SilentBasicAuthenticationMechanism(RESTHEART_REALM));
            }

            return buildSecurityHandlerChain(next, accessManager, identityManager, mechanisms);
        } else {
            return next;
        }
    }

    /**
     *
     * @param next
     * @param authenticationMechanism
     * @param identityManager
     * @param accessManager
     * @param challenging false if never challenge for authentication (don't
     * sent the WWW-Authenticate response header)
     */
    public SecurityHandler(final PipedHttpHandler next, AuthenticationMechanism authenticationMechanism, final IdentityManager identityManager, final AccessManager accessManager, final boolean challenging) {
        super(getSecurityHandlerChain(next, authenticationMechanism, identityManager, accessManager, challenging));
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        next(exchange, context);
    }

}
