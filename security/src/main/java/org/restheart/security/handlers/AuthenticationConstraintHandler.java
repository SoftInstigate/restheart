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
import io.undertow.server.HttpServerExchange;
import java.util.LinkedHashSet;
import org.restheart.security.plugins.Authorizer;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticationConstraintHandler extends PipedHttpHandler {

    private final LinkedHashSet<Authorizer> authorizers;

    /**
     *
     * @param next
     * @param authorizers
     */
    public AuthenticationConstraintHandler(PipedHttpHandler next, 
            LinkedHashSet<Authorizer> authorizers) {
        super(next);
        this.authorizers = authorizers;
    }

    protected boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        return authorizers == null 
                ? false 
                : authorizers
                        .stream()
                        .allMatch(a -> a.isAuthenticationRequired(exchange));
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (isAuthenticationRequired(exchange)) {
            SecurityContext scontext = exchange.getSecurityContext();
            scontext.setAuthenticationRequired();
        }

        next(exchange);
    }
}
