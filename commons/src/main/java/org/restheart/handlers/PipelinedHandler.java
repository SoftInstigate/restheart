/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
import java.util.Objects;

/**
 * base class to implement a PipelinedHandler
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class PipelinedHandler implements HttpHandler {

    protected static final String CONTENT_TYPE = "contentType";

    private PipelinedHandler next;

    /**
     * Creates a default instance of PipedHttpHandler with next = null
     */
    public PipelinedHandler() {
        this(null);
    }

    /**
     *
     * @param next the next handler in this chain
     */
    public PipelinedHandler(PipelinedHandler next) {
        this.next = next;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public abstract void handleRequest(HttpServerExchange exchange) throws Exception;

    /**
     * @return the next PipedHttpHandler
     */
    protected PipelinedHandler getNext() {
        return next;
    }

    /**
     * set the next PipedHttpHandler
     *
     * @param next
     */
    protected void setNext(PipelinedHandler next) {
        this.next = next;
    }

    protected void next(HttpServerExchange exchange) throws Exception {
        if (this.next != null) {
            this.next.handleRequest(exchange);
        }
    }

    /**
     * pipes multiple PipelinedHandler in a pipeline
     *
     * @param handlers
     * @return
     */
    public static PipelinedHandler pipe(PipelinedHandler... handlers) {
        if (Objects.isNull(handlers)) {
            return null;
        }

        for (var idx = 0; idx < handlers.length - 1; idx++) {
            handlers[idx].setNext(handlers[idx + 1]);
        }

        return handlers[0];
    }
}
