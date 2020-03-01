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

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import java.util.Set;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.Authorizer;
import org.restheart.security.plugins.PluginsRegistry;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GlobalSecurityPredicatesAuthorizer extends PipedHttpHandler {

    private final Set<PluginRecord<Authorizer>> authorizers;

    /**
     * Creates a new instance of AccessManagerHandler
     *
     * @param authorizers
     * @param next
     */
    public GlobalSecurityPredicatesAuthorizer(
            Set<PluginRecord<Authorizer>> authorizers,
            PipelinedHandler next) {
        super(next);
        this.authorizers = authorizers;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (isAllowed(exchange)
                && checkGlobalPredicates(exchange)) {
            next(exchange);
        } else {
            // add CORS headers
            CORSHandler.injectAccessControlAllowHeaders(exchange);
            // set status code and end exchange
            exchange.setStatusCode(HttpStatus.SC_FORBIDDEN);
            exchange.endExchange();
        }
    }

    /**
     *
     * @param exchange
     * @return true if no global security predicate denies the request and any
     * accessManager allows the request
     */
    private boolean isAllowed(HttpServerExchange exchange) {
        if (getGlobalSecurityPredicates() != null
                && !checkGlobalPredicates(exchange)) {
            return false;
        }

        if (authorizers == null) {
            return true;
        } else {
            return authorizers.stream()
                    .filter(a -> a.getInstance() != null)
                    .filter(a -> a.isEnabled())
                    .anyMatch(a -> a.getInstance().isAllowed(exchange));
        }
    }

    /**
     *
     * @param exchange
     * @return true if all global security predicates resolve the request
     */
    private boolean checkGlobalPredicates(HttpServerExchange exchange) {
        return PluginsRegistry.getInstance()
                .getGlobalSecurityPredicates()
                .stream()
                .allMatch(predicate -> predicate.resolve(exchange));
    }

    /**
     * global security predicates must all resolve to true to allow the request
     *
     * @deprecated use
     * PluginsRegistry.getInstance().getGlobalSecurityPredicates() instead
     * @return the globalSecurityPredicates allow to get and set the global
     * security predicates to apply to all requests
     */
    @Deprecated
    public static Set<Predicate> getGlobalSecurityPredicates() {
        return PluginsRegistry.getInstance()
                .getGlobalSecurityPredicates();
    }
}
