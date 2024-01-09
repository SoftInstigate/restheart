/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.handlers;

import java.util.Arrays;
import java.util.Objects;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

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

        // remove null entries in handlers array
        handlers = Arrays.stream(handlers)
            .filter(s -> s != null)
            .toArray(PipelinedHandler[]::new);

        for (var idx = 0; idx < handlers.length - 1; idx++) {
            handlers[idx].setNext(handlers[idx + 1]);
        }

        return handlers[0];
    }
}
