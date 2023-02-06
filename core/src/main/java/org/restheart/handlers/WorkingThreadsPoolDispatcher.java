/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
import io.undertow.server.handlers.BlockingHandler;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 *         Dispatches the execution of the service to the Working Thread Pool
 *
 *         This applies to Services annotated
 *         with @RegisterPlugin(blocking=true)
 *
 *         Services with @RegisterPlugin(blocking=false) are not dispacthed to
 *         the Working Thread Pool
 *         and executed directly by the IO Thread
 *
 */
public class WorkingThreadsPoolDispatcher extends PipelinedHandler {
    private final BlockingHandler blockingHandler = new BlockingHandler(this);

    /**
     * Creates a new instance of PipelineInfoInjector
     *
     */
    public WorkingThreadsPoolDispatcher() {
        this(null);
    }

    /**
     * Creates a new instance of ThreadExecutorDispatcher
     *
     * @param next
     */
    public WorkingThreadsPoolDispatcher(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            blockingHandler.handleRequest(exchange);
        } else {
            next(exchange);
        }
    }
}
