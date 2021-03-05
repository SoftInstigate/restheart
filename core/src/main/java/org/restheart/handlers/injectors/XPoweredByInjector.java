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
package org.restheart.handlers.injectors;

import com.google.common.net.HttpHeaders;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.restheart.handlers.PipelinedHandler;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * It injects the X-Powered-By response header
 */
public class XPoweredByInjector extends PipelinedHandler {
    /**
     * Creates a new instance of XPoweredByInjector
     *
     * @param next
     */
    public XPoweredByInjector(PipelinedHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of XPoweredByInjector
     *
     */
    public XPoweredByInjector() {
        super(null);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders().add(HttpString.tryFromString(
                HttpHeaders.X_POWERED_BY), "restheart.org");

        next(exchange);
    }
}
