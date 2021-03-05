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
import java.util.List;
import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.Service;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.PluginUtils;
import static org.restheart.utils.PluginUtils.requiresContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes response interceptors t
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseInterceptorsExecutor extends PipelinedHandler {

    static final Logger LOGGER = LoggerFactory.getLogger(ResponseInterceptorsExecutor.class);

    private final boolean filterRequiringContent;

    private final PluginsRegistry pluginsRegistry = PluginsRegistryImpl.getInstance();

    public ResponseInterceptorsExecutor() {
        this(null, false);
    }

    public ResponseInterceptorsExecutor(boolean filterRequiringContent) {
        this(null, filterRequiringContent);
    }

    /**
     * Construct a new instance.
     *
     * @param next
     * @param filterRequiringContent if true does not execute the interceptors that
     *                               require content
     */
    public ResponseInterceptorsExecutor(PipelinedHandler next, boolean filterRequiringContent) {
        super(next);
        this.filterRequiringContent = filterRequiringContent;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Request request;
        Response response;

        var handlingService = PluginUtils.handlingService(pluginsRegistry, exchange);

        if (handlingService != null) {
            request = ServiceRequest.of(exchange, ServiceRequest.class);
            response = ServiceResponse.of(exchange, ServiceResponse.class);
        } else {
            request = ByteArrayProxyRequest.of(exchange);
            response = ByteArrayProxyResponse.of(exchange);
        }

        if (!Exchange.responseInterceptorsExecuted(exchange)) {
            Exchange.setResponseInterceptorsExecuted(exchange);
            executeAsyncResponseInterceptor(exchange, handlingService, request, response);
            executeResponseInterceptor(exchange, handlingService, request, response);
        }

        next(exchange);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void executeResponseInterceptor(HttpServerExchange exchange, Service handlingService, Request request,
            Response response) {

        Exchange.setResponseInterceptorsExecuted(exchange);

        List<Interceptor> inteceptors;

        if (handlingService != null) {
            inteceptors = this.pluginsRegistry.getServiceInterceptors(handlingService, InterceptPoint.RESPONSE);
        } else {
            inteceptors = this.pluginsRegistry.getProxyInterceptors(InterceptPoint.RESPONSE);
        }

        inteceptors.stream()
                .filter(ri -> !this.filterRequiringContent || !requiresContent(ri)).filter(ri -> {
                    try {
                        return ri.resolve(request, response);
                    } catch (Exception e) {
                        LOGGER.warn("Error resolving interceptor {} for {} on intercept point {}",
                                ri.getClass().getSimpleName(), exchange.getRequestPath(), InterceptPoint.RESPONSE, e);

                        return false;
                    }
                }).forEachOrdered(ri -> {
                    LOGGER.debug("Executing interceptor {} for {} on intercept point {}",
                                PluginUtils.name(ri),
                        exchange.getRequestPath(),
                        InterceptPoint.RESPONSE);

                    try {
                        ri.handle(request, response);
                    } catch (Exception ex) {
                        LOGGER.error("Error executing interceptor {} for {} on intercept point {}",
                            PluginUtils.name(ri),
                            exchange.getRequestPath(),
                            InterceptPoint.RESPONSE, ex);

                        Exchange.setInError(exchange);
                        LambdaUtils.throwsSneakyException(ex);
                    }
                });
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void executeAsyncResponseInterceptor(HttpServerExchange exchange,
            Service handlingService,
            Request request,
            Response response) {

        List<Interceptor> inteceptors;

        if (handlingService != null) {
            inteceptors = this.pluginsRegistry.getServiceInterceptors(handlingService, InterceptPoint.RESPONSE_ASYNC);
        } else {
            inteceptors = this.pluginsRegistry.getProxyInterceptors(InterceptPoint.RESPONSE_ASYNC);
        }

        Exchange.setResponseInterceptorsExecuted(exchange);

        inteceptors.stream()
                .filter(ri -> !this.filterRequiringContent || !requiresContent(ri))
                .filter(ri -> {
                    try {
                        return ri.resolve(request, response);
                    } catch (Exception e) {
                        LOGGER.warn("Error resolving interceptor {} for {} on intercept point {}",
                            ri.getClass().getSimpleName(),
                            exchange.getRequestPath(),
                            InterceptPoint.RESPONSE_ASYNC);

                        return false;
                    }
                })
                .forEachOrdered(ri -> {
                    exchange.getConnection().getWorker().execute(() -> {
                        LOGGER.debug("Executing interceptor {} for {} on intercept point {}",
                            PluginUtils.name(ri),
                            exchange.getRequestPath(),
                            InterceptPoint.RESPONSE_ASYNC);

                        try {
                            ri.handle(request, response);
                        } catch (Exception ex) {
                            LOGGER.error("Error executing interceptor {} for {} on intercept point {}",
                                PluginUtils.name(ri),
                                exchange.getRequestPath(),
                                InterceptPoint.RESPONSE_ASYNC,
                                ex);

                            Exchange.setInError(exchange);
                            LambdaUtils.throwsSneakyException(ex);
                        }
                    });
                });
    }

}
