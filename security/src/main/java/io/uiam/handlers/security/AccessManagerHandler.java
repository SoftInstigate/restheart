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

import io.undertow.server.HttpServerExchange;
import java.util.HashSet;
import java.util.Set;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;
import io.uiam.RequestContextPredicate;
import io.uiam.utils.HttpStatus;
import io.uiam.plugins.authorization.PluggableAccessManager;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AccessManagerHandler extends PipedHttpHandler {

    private final PluggableAccessManager accessManager;
    private static Set<RequestContextPredicate> globalSecurityPredicates
            = new HashSet<>();

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
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange hse,
            RequestContext context) throws Exception {
        if (accessManager.isAllowed(hse, context)
                && checkGlobalPredicates(hse, context)) {
            next(hse, context);
        } else {
            hse.setStatusCode(HttpStatus.SC_FORBIDDEN);
            hse.endExchange();
        }
    }

    private boolean checkGlobalPredicates(HttpServerExchange hse,
            RequestContext context) {
        return this.getGlobalSecurityPredicates()
                .stream()
                .allMatch(predicate -> predicate.resolve(hse, context));
    }

    /**
     * @return the globalSecurityPredicates allow to get and set the global
     * security predicates to apply to all requests
     */
    public static Set<RequestContextPredicate> getGlobalSecurityPredicates() {
        return globalSecurityPredicates;
    }
}
