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
import java.util.Set;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.Authorizer;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticationConstraintHandler extends PipelinedHandler {

    private final Set<PluginRecord<Authorizer>> authorizers;

    /**
     *
     * @param next
     * @param authorizers
     */
    public AuthenticationConstraintHandler(PipelinedHandler next,
            Set<PluginRecord<Authorizer>> authorizers) {
        super(next);
        this.authorizers = authorizers;
    }

    protected boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        return authorizers == null
                ? false
                : authorizers
                        .stream()
                        .filter(a -> a.isEnabled())
                        .filter(a -> a.getInstance() != null)
                        .allMatch(a -> a.getInstance().isAuthenticationRequired(exchange));
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
