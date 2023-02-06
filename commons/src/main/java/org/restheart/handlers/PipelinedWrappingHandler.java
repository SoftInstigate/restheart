/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Service;

/**
 * wraps a HttpHandler into a PipelinedHttpHandler
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PipelinedWrappingHandler extends PipelinedHandler {

    private final HttpHandler wrapped;

    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param next
     * @param handler
     */
    private PipelinedWrappingHandler(PipelinedHandler next, HttpHandler handler) {
        super(next);
        wrapped = handler;
    }

    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param next
     * @param service
     */
    private <R extends ServiceRequest<?>, S extends ServiceResponse<?>> PipelinedWrappingHandler(PipelinedHandler next, Service<R, S> service) {
        super(next);
        wrapped = new ServiceWrapper<>(service);
    }

    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param handler
     */
    private PipelinedWrappingHandler(HttpHandler handler) {
        super(null);
        wrapped = handler;
    }

    /**
     *
     * @param handler
     * @return the wrapping handler
     */
    public static PipelinedWrappingHandler wrap(HttpHandler handler) {
        return wrap(null, handler);
    }

    /**
     *
     * @param <R> Request
     * @param <S> Response
     * @param service
     * @return the wrapping handler
     */
    public static <R extends ServiceRequest<?>, S extends ServiceResponse<?>> PipelinedWrappingHandler wrap(Service<R, S> service) {
        return wrap(null, service);
    }

    /**
     *
     * @param next
     * @param handler
     * @return the wrapping handler
     */
    public static PipelinedWrappingHandler wrap(PipelinedHandler next, HttpHandler handler) {
        return new PipelinedWrappingHandler(next, handler);
    }

    /**
     *
     * @param <R> Request
     * @param <S> Response
     * @param next
     * @param service
     * @return the wrapping handler
     */
    public static <R extends ServiceRequest<?>, S extends ServiceResponse<?>> PipelinedWrappingHandler wrap(PipelinedHandler next, Service<R, S> service) {
        return new PipelinedWrappingHandler(next, service);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (wrapped == null) {
            next(exchange);
        } else {
            wrapped.handleRequest(exchange);

            if (!exchange.isResponseComplete()) {
                next(exchange);
            }
        }
    }
}

class ServiceWrapper<R extends ServiceRequest<?>, S extends ServiceResponse<?>> extends PipelinedHandler {
    final Service<R, S> service;

    ServiceWrapper(Service<R, S> service) {
        this.service = service;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        service.handle(service.request().apply(exchange),
                service.response().apply(exchange));
    }
}
