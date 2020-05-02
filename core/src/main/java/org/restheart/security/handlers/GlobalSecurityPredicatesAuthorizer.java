/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.handlers;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import java.util.Set;
import org.restheart.exchange.Request;
import org.restheart.handlers.CORSHandler;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.security.Authorizer;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GlobalSecurityPredicatesAuthorizer extends PipelinedHandler {

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
        return PluginsRegistryImpl.getInstance()
                .getGlobalSecurityPredicates();
    }

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
        var request = Request.of(exchange);

        if (isAllowed(request)
                && checkGlobalPredicates(request)) {
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
    private boolean isAllowed(final Request request) {
        if (getGlobalSecurityPredicates() != null
                && !checkGlobalPredicates(request)) {
            return false;
        }

        if (authorizers == null) {
            return true;
        } else {
            return authorizers.stream()
                    .filter(a -> a.getInstance() != null)
                    .filter(a -> a.isEnabled())
                    .anyMatch(a -> a.getInstance().isAllowed(request));
        }
    }

    /**
     *
     * @param exchange
     * @return true if all global security predicates resolve the request
     */
    private boolean checkGlobalPredicates(final Request request) {
        return PluginsRegistryImpl.getInstance()
                .getGlobalSecurityPredicates()
                .stream()
                .allMatch(predicate -> predicate.resolve(request.getExchange()));
    }

}
