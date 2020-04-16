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
import java.util.Arrays;
import org.restheart.exchange.AbstractExchange;
import org.restheart.exchange.AbstractRequest;
import org.restheart.exchange.AbstractResponse;
import org.restheart.exchange.BufferedByteArrayRequest;
import org.restheart.exchange.BufferedByteArrayResponse;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.PluginUtils;
import static org.restheart.utils.PluginUtils.interceptPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestInterceptorsExecutor extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RequestInterceptorsExecutor.class);

    private final ResponseSender sender = new ResponseSender();

    private final InterceptPoint interceptPoint;

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
    public RequestInterceptorsExecutor(PipelinedHandler next,
            InterceptPoint interceptPoint) {
        super(next);
        this.interceptPoint = interceptPoint;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // if the request is handled by a service set to not execute interceptors
        // at this interceptPoint, skip interceptors execution
        var vip = PluginUtils.dontIntercept(
                PluginsRegistryImpl.getInstance(), exchange);

        if (Arrays.stream(vip).anyMatch(interceptPoint::equals)) {
            next(exchange);
            return;
        }

        AbstractRequest request;
        AbstractResponse response;

        var handlingService = PluginUtils.handlingService(
                PluginsRegistryImpl.getInstance(),
                exchange);

        if (handlingService != null) {
            request = Request.of(exchange);
            response = Response.of(exchange);
        } else {
            request = BufferedByteArrayRequest.wrap(exchange);
            response = BufferedByteArrayResponse.wrap(exchange);
        }

        PluginsRegistryImpl
                .getInstance()
                .getInterceptors()
                .stream()
                .filter(ri -> ri.isEnabled())
                .map(ri -> ri.getInstance())
                // IMPORTANT: An interceptor can intercept 
                // - requests handled by a Service when its request and response 
                //   types are equal to the ones declared by the Service
                // - request handled by a Proxy when its request and response 
                //   are BufferedByteArrayRequest and BufferedByteArrayResponse
                .filter(ri -> 
                        (handlingService == null
                && ri.requestType().equals(BufferedByteArrayRequest.type())
                && ri.responseType().equals(BufferedByteArrayResponse.type()))
                || (handlingService != null
                && ri.requestType().equals(handlingService.requestType())
                && ri.responseType().equals(handlingService.responseType())))
                .filter(ri -> interceptPoint == interceptPoint(ri))
                .filter(ri -> {
                    try {
                        return ri.resolve(request, response);
                    } catch (Exception e) {
                        LOGGER.warn("Error resolving interceptor {} for {} on intercept point {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                interceptPoint,
                                e);

                        return false;
                    }
                })
                .forEachOrdered(ri -> {
                    try {
                        LOGGER.debug("Executing request interceptor {} for {} on intercept point {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                interceptPoint);

                        ri.handle(request, response);
                    } catch (Exception ex) {
                        LOGGER.error("Error executing request interceptor {} for {} on intercept point {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                interceptPoint,
                                ex);
                        AbstractExchange.setInError(exchange);
                        LambdaUtils.throwsSneakyExcpetion(ex);
                    }
                });

        // if an interceptor sets the response as errored
        // stop processing the request and send the response
        if (AbstractExchange.isInError(exchange)) {
            // if in error but no status code use 400 Bad Request
            if (response.getStatusCode() < 0) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }

            sender.handleRequest(exchange);
        } else {
            next(exchange);
        }
    }
}
