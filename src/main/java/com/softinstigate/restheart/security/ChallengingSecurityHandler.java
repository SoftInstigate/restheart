/*
 * Copyright 2014 SoftInstigate.
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
package com.softinstigate.restheart.security;

import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.PipedWrappingHandler;
import static com.softinstigate.restheart.security.RestheartIdentityManager.RESTHEART_REALM;
import com.softinstigate.restheart.security.handlers.AccessManagerHandler;
import com.softinstigate.restheart.security.handlers.PredicateAuthenticationConstraintHandler;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author uji
 */
public class ChallengingSecurityHandler extends PipedWrappingHandler
{
    public ChallengingSecurityHandler(final PipedHttpHandler next, final IdentityManager identityManager, final AccessManager accessManager)
    {
        super(next, getSecurityHandlerChain(identityManager, accessManager));
    }

    private static HttpHandler getSecurityHandlerChain(final IdentityManager identityManager, final AccessManager accessManager)
    {
        if (identityManager != null)
        {
            HttpHandler handler = null;

            if (accessManager != null)
            {
                handler = new AccessManagerHandler(accessManager, null);
            }

            handler = new AuthenticationCallHandler(handler);
            handler = new PredicateAuthenticationConstraintHandler(handler, accessManager);
            final List<AuthenticationMechanism> mechanisms = Collections.<AuthenticationMechanism>singletonList(new BasicAuthenticationMechanism(RESTHEART_REALM));
            handler = new AuthenticationMechanismsHandler(handler, mechanisms);
            handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);

            return handler;
        }
        else
        {
            return null;
        }
    }
}
