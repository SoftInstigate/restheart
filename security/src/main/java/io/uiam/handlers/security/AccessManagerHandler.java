/*
 * uIAM - the IAM for microservices
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
package io.uiam.handlers.security;

import java.util.HashSet;
import java.util.Set;

import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.authorization.PluggableAccessManager;
import io.uiam.utils.HttpStatus;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AccessManagerHandler extends PipedHttpHandler {

    private final PluggableAccessManager accessManager;
    private static Set<Predicate> globalSecurityPredicates = new HashSet<>();

    /**
     * Creates a new instance of AccessManagerHandler
     *
     * @param accessManager
     * @param next
     */
    public AccessManagerHandler(PluggableAccessManager accessManager, PipedHttpHandler next) {
        super(next);
        this.accessManager = accessManager;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (accessManager.isAllowed(exchange)
                && checkGlobalPredicates(exchange)) {
            next(exchange);
        } else {
            exchange.setStatusCode(HttpStatus.SC_FORBIDDEN);
            exchange.endExchange();
        }
    }

    private boolean checkGlobalPredicates(HttpServerExchange hse) {
        return this.getGlobalSecurityPredicates()
                .stream()
                .allMatch(predicate -> predicate.resolve(hse));
    }

    /**
     * @return the globalSecurityPredicates allow to get and set the global
     * security predicates to apply to all requests
     */
    public static Set<Predicate> getGlobalSecurityPredicates() {
        return globalSecurityPredicates;
    }
}
