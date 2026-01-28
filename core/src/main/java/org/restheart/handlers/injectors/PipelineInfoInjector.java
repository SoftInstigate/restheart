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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.Request;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * Injects the PipelineInfo to allows to programmatically understand which
 * pipeline (service, proxy or static resource) is handling the request via
 * Request.getPipelineInfo()
 */
public class PipelineInfoInjector extends PipelinedHandler {
    private final PluginsRegistry pluginsRegistry = PluginsRegistryImpl.getInstance();

    /**
     * Creates a new instance of PipelineInfoInjector
     *
     * @param next
     */
    public PipelineInfoInjector(PipelinedHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of PipelineInfoInjector
     *
     */
    public PipelineInfoInjector() {
        this(null);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Request.setPipelineInfo(exchange, pluginsRegistry.getPipelineInfo(exchange.getRequestPath()));

        next(exchange);
    }
}
