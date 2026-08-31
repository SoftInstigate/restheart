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

import java.util.ArrayList;
import java.util.List;

import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.logging.RequestPhaseContext;
import org.restheart.logging.RequestPhaseContext.Phase;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.InterceptorException;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.Service;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestInterceptorsExecutor extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestInterceptorsExecutor.class);

    private final ResponseSender sender = new ResponseSender();

    private final InterceptPoint interceptPoint;

    private final PluginsRegistry pluginsRegistry = PluginsRegistryImpl.getInstance();

    /**
     *
     * @param interceptPoint
     */
    public RequestInterceptorsExecutor(InterceptPoint interceptPoint) {
        super(null);
        this.interceptPoint = interceptPoint;
    }

    /**
     * @param next
     * @param interceptPoint
     */
    public RequestInterceptorsExecutor(PipelinedHandler next, InterceptPoint interceptPoint) {
        super(next);
        this.interceptPoint = interceptPoint;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Request<?> request;
        Response<?> response;

        var handlingService = (Service<ServiceRequest<?>, ServiceResponse<?>>) PluginUtils.handlingService(pluginsRegistry, exchange);

        RequestPhaseContext.setPhase(Phase.PHASE_START);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} INTERCEPTORS for {} {}", interceptPoint, exchange.getRequestMethod(), exchange.getRequestPath());
        }

        List<Interceptor<?, ?>> interceptors;

        if (handlingService != null) {
            request = ServiceRequest.of(exchange, ServiceRequest.class);
            response = ServiceResponse.of(exchange, ServiceResponse.class);
            interceptors = pluginsRegistry.getServiceInterceptors(handlingService, interceptPoint);
        } else {
            request = ByteArrayProxyRequest.of(exchange);
            response = ByteArrayProxyResponse.of(exchange);
            interceptors = pluginsRegistry.getProxyInterceptors(interceptPoint);
        }

        if (interceptors.isEmpty()) {
            RequestPhaseContext.setPhase(Phase.INFO);
            LOGGER.debug("No interceptors found");
            RequestPhaseContext.setPhase(Phase.PHASE_END);
            LOGGER.debug("{} COMPLETED in 0ms", interceptPoint);
            RequestPhaseContext.reset();
            next(exchange);
            return;
        }

        // interceptors is already List<Interceptor<?,?>> - the instanceof/cast pair this
        // replaced only stripped generics to the raw type so resolve()/handle() below can be
        // called without a wildcard-capture mismatch against request/response (hence the
        // rawtypes suppression on the method); it never filtered anything out. Stays null
        // until the first match so the common "nothing resolves" case allocates nothing.
        List<Interceptor> resolvedInterceptors = null;
        for (var ri : interceptors) {
            var interceptor = (Interceptor) ri;
            try {
                if (interceptor.resolve(request, response)) {
                    if (resolvedInterceptors == null) {
                        resolvedInterceptors = new ArrayList<>(interceptors.size());
                    }
                    resolvedInterceptors.add(interceptor);
                }
            } catch (Exception ex) {
                LOGGER.warn("Error resolving interceptor {} for {} on intercept point {}", interceptor.getClass().getSimpleName(), exchange.getRequestPath(), interceptPoint, ex);

                Exchange.setInError(exchange);
                LambdaUtils.throwsSneakyException(new InterceptorException("Error resolving interceptor " + interceptor.getClass().getSimpleName(), ex));
            }
        }
        if (resolvedInterceptors == null) {
            resolvedInterceptors = List.of();
        }

        RequestPhaseContext.setPhase(Phase.INFO);
        LOGGER.debug("Found {} interceptors", resolvedInterceptors.size());

        // executionStartTime is only ever read by the debug log after the loop, so it's
        // skipped entirely when debug is disabled instead of paying for a clock read that
        // nothing will use. Per-interceptor timing below can't do the same: the error branch
        // needs a duration unconditionally, so nanoTime() is used there for resolution instead
        // (currentTimeMillis() is coarse enough to systematically show 0ms for fast interceptors).
        var executionStartTime = LOGGER.isDebugEnabled() ? System.nanoTime() : 0L;
        var totalInterceptors = resolvedInterceptors.size();

        for (int i = 0; i < totalInterceptors; i++) {
            var ri = resolvedInterceptors.get(i);
            var interceptorStartTime = System.nanoTime();

            try {
                RequestPhaseContext.setPhase(Phase.ITEM);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("{} (priority: {})", PluginUtils.name(ri), PluginUtils.priority(ri));
                }

                ri.handle(request, response);

                RequestPhaseContext.setPhase(Phase.SUBITEM);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("✓ {}ms", (System.nanoTime() - interceptorStartTime) / 1_000_000);
                }
            } catch (Exception ex) {
                var interceptorDurationMs = (System.nanoTime() - interceptorStartTime) / 1_000_000;
                RequestPhaseContext.setPhase(Phase.SUBITEM);
                LOGGER.error("✗ FAILED after {}ms: {}", interceptorDurationMs, ex.getMessage());

                Exchange.setInError(exchange);
                LambdaUtils.throwsSneakyException(new InterceptorException("Error executing interceptor " + ri.getClass().getSimpleName(), ex));
            }
        }

        RequestPhaseContext.setPhase(Phase.PHASE_END);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} COMPLETED in {}ms", interceptPoint, (System.nanoTime() - executionStartTime) / 1_000_000);
        }
        RequestPhaseContext.reset();

        // If an interceptor sets the response as errored
        // stop processing the request and send the response
        // This happens AFTER_AUTH, otherwise not authenticated requests
        // might snoop information. For instance, a request to MongoService might
        // be able to check if a collection exists (this check is done by
        // BEFORE_AUTH interceptor CollectionPropsInjector)
        if (this.interceptPoint == InterceptPoint.REQUEST_AFTER_AUTH && Exchange.isInError(exchange)) {
            // if in error but no status code use 400 Bad Request
            if (response.getStatusCode() < 0) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }

            // if this is a service, handle OPTIONS
            // otherwise requests with bad content receive CORS errors
            if (handlingService != null && request.isOptions()) {
                handlingService.handleOptions(ServiceRequest.of(exchange, ServiceRequest.class));
            }

            sender.handleRequest(exchange);
        } else {
            next(exchange);
        }
    }
}
