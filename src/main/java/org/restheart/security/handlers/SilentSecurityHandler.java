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
import org.restheart.handlers.PipedWrappingHandler;
import org.restheart.security.AccessManager;
import org.restheart.security.SilentBasicAuthenticationMechanism;
import static org.restheart.security.RestheartIdentityManager.RESTHEART_REALM;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Andrea Di Cesare
 */
public class SilentSecurityHandler extends PipedWrappingHandler {

    /**
     *
     * @param next
     * @param identityManager
     * @param accessManager
     */
    public SilentSecurityHandler(final PipedHttpHandler next, final IdentityManager identityManager, final AccessManager accessManager) {
        super(next, getSecurityHandlerChain(identityManager, accessManager));
    }

    private static HttpHandler getSecurityHandlerChain(final IdentityManager identityManager, final AccessManager accessManager) {
        HttpHandler handler = null;
        if (identityManager != null) {
            final List<AuthenticationMechanism> mechanisms = Collections.<AuthenticationMechanism>singletonList(
                    new SilentBasicAuthenticationMechanism(RESTHEART_REALM));
            handler = buildSecurityHandlerChain(accessManager, null, identityManager, mechanisms);
        }
        return handler;
    }
}
