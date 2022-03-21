/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipelinedHandler;

/**
 *
 * injects the context authenticatedAccount
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthHeadersRemover extends PipelinedHandler {
    /**
     * Creates a new instance of AuthHeadersRemover
     *
     */
    public AuthHeadersRemover() {
        super();
    }

    /**
     * Creates a new instance of AuthHeadersRemover
     *
     * @param next
     */
    public AuthHeadersRemover(PipelinedHandler next) {
        super(next);
    }

    /**
     * before proxyng the request the authentication headers are removed
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.getRequestHeaders().remove("Authorization");

        next(exchange);
    }
}
