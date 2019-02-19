/*
 * uIAM - the IAM for microservices
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
