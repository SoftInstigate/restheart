/*
 * Copyright 2014 - 2015 SoftInstigate.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.handlers;

import org.restheart.handlers.PipedHttpHandler;
import org.restheart.security.AccessManager;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.List;
import org.restheart.handlers.RequestContext;
import org.restheart.security.AuthTokenAuthenticationMechanism;
import static org.restheart.security.RestheartIdentityManager.RESTHEART_REALM;
import org.restheart.security.SilentBasicAuthenticationMechanism;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SecurityHandler extends PipedHttpHandler {
    /**
     *
     * @param next
     * @param identityManager
     * @param accessManager
     * @param challenging false if never challenge for authentication (don't
     * sent the WWW-Authenticate response header)
     */
    public SecurityHandler(final PipedHttpHandler next, final IdentityManager identityManager, final AccessManager accessManager, final boolean challenging) {
        super(getSecurityHandlerChain(next, identityManager, accessManager, challenging));
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        getNext().handleRequest(exchange, context);
    }

    private static PipedHttpHandler getSecurityHandlerChain(final PipedHttpHandler next, final IdentityManager identityManager, final AccessManager accessManager, final boolean challenging) {
        if (identityManager != null) {
            final List<AuthenticationMechanism> mechanisms = new ArrayList<>();

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
}
