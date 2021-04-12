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

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import java.util.Set;
import org.restheart.exchange.Request;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;
import org.restheart.utils.PluginUtils;

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
    public AuthenticationConstraintHandler(PipelinedHandler next, Set<PluginRecord<Authorizer>> authorizers) {
        super(next);
        this.authorizers = authorizers;
    }

    /**
     *
     * @param exchange
     * @return true if all defined authorizers requires authentication
     */
    protected boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        return authorizers == null
                ? false
                : authorizers
                        .stream()
                        .filter(a -> a.isEnabled())
                        .filter(a -> a.getInstance() != null)
                        .map(a -> a.getInstance())
                        .filter(a -> PluginUtils.authorizerType(a) == TYPE.ALLOWER)
                        .allMatch(a -> a.isAuthenticationRequired(Request.of(exchange)));
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
