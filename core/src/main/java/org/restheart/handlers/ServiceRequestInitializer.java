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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import org.restheart.plugins.PluginsRegistryImpl;

/**
 * Initializes the Request invoking
 * Service.requestInitializer().accept(exchange)
 *
 * Service.requestInitializer() is a Consumer that allows the Service to
 * initialize its own implementation of Request, for instance parsing the
 * request body according to its needs
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ServiceRequestInitializer extends PipelinedHandler {

    /**
     * Creates a new instance of RequestInitializer
     *
     */
    public ServiceRequestInitializer() {
        super();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var registry = PluginsRegistryImpl.getInstance();
        var path = exchange.getRequestPath();

        var pi = registry.getPipelineInfo(path);

        var srv = registry.getServices().stream()
                .filter(s -> s.getName().equals(pi.getName()))
                .findAny();

        if (srv.isPresent()) {
            srv.get().getInstance()
                    .requestInitializer()
                    .accept(exchange);
        }

        next(exchange);
    }
}
