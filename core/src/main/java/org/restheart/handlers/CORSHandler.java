/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import io.undertow.server.HttpServerExchange;
import static org.restheart.exchange.CORSHeaders.ACCESS_CONTROL_ALLOW_CREDENTIAL;
import static org.restheart.exchange.CORSHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.restheart.exchange.CORSHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;

import org.restheart.exchange.Request;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.utils.PluginUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * Adds the response CORS headers defined by the Service's accessControl*() methods
 *
 */
public class CORSHandler extends PipelinedHandler {
    public static void injectAccessControlAllowHeaders(HttpServerExchange exchange) {
        var handlingService = PluginUtils.handlingService(PluginsRegistryImpl.getInstance(), exchange);

        if (handlingService == null) {
            // it must be a proxied or static resource
            return;
        }

        var request = Request.of(exchange);

        // Check if CORS is enabled for this service
        if (!handlingService.corsEnabled(request)) {
            return;
        }

        var responseHeaders = exchange.getResponseHeaders();

        if (!responseHeaders.contains(ACCESS_CONTROL_ALLOW_ORIGIN)) {
            responseHeaders.add(ACCESS_CONTROL_ALLOW_ORIGIN, handlingService.accessControlAllowOrigin(request));
        }

        if (!responseHeaders.contains(ACCESS_CONTROL_ALLOW_CREDENTIAL)) {
            responseHeaders.add(ACCESS_CONTROL_ALLOW_CREDENTIAL, handlingService.accessControlAllowCredentials(request));
        }

        if (!responseHeaders.contains(ACCESS_CONTROL_EXPOSE_HEADERS)) {
            responseHeaders.add(ACCESS_CONTROL_EXPOSE_HEADERS, handlingService.accessControlExposeHeaders(request));
        }
    }

    /**
     * Creates a new instance of CORSHandler
     *
     */
    public CORSHandler() {
        super();
    }

    /**
     * Creates a new instance of CORSHandler
     *
     * @param next
     */
    public CORSHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        injectAccessControlAllowHeaders(exchange);
        next(exchange);
    }
}
