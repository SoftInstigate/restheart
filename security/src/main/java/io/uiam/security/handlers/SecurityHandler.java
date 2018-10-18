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
package io.uiam.security.handlers;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.List;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;
import io.uiam.security.AccessManager;
import io.uiam.security.AuthTokenAuthenticationMechanism;
import io.uiam.security.SilentBasicAuthenticationMechanism;
import static io.uiam.security.uIAMIdentityManager.UIAM_REALM;

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

            mechanisms.add(new AuthTokenAuthenticationMechanism(UIAM_REALM));

            if (challenging) {
                mechanisms.add(new BasicAuthenticationMechanism(UIAM_REALM));
            } else {
                mechanisms.add(new SilentBasicAuthenticationMechanism(UIAM_REALM));
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
