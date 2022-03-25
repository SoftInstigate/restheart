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
package org.restheart.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestNotManagedHandler extends PipelinedHandler {
    /**
     * Creates a new instance of RequestProxyHandler
     *
     */
    public RequestNotManagedHandler() {
        super(null);
    }

    /**
     * Creates a new instance of RequestProxyHandler
     *
     * @param next
     */
    public RequestNotManagedHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of RequestProxyHandler
     *
     * @param handler
     */
    public RequestNotManagedHandler(HttpHandler handler) {
        super(null);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.setStatusCode(StatusCodes.BAD_GATEWAY);
        exchange.endExchange();
    }
}
