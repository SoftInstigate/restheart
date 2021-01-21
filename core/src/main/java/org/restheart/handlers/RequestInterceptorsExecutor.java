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
import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.PluginUtils;
import static org.restheart.utils.PluginUtils.cachedRequestType;
import static org.restheart.utils.PluginUtils.cachedResponseType;
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

    private final PluginsRegistry pluginsRegistry;

    /**
     *
     * @param interceptPoint
     */
    public RequestInterceptorsExecutor(InterceptPoint interceptPoint) {
        super(null);
        this.interceptPoint = interceptPoint;
        this.pluginsRegistry = PluginsRegistryImpl.getInstance();
    }

    /**
     * @param next
     * @param interceptPoint
     */
    public RequestInterceptorsExecutor(PipelinedHandler next,
            InterceptPoint interceptPoint) {
        super(next);
        this.interceptPoint = interceptPoint;
        this.pluginsRegistry = PluginsRegistryImpl.getInstance();
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    @SuppressWarnings({"unchecked","rawtypes"})
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Request request;
        Response response;

        var handlingService = PluginUtils.handlingService(pluginsRegistry, exchange);

        if (handlingService != null) {
            // if the request is handled by a service set to not execute interceptors
            // at this interceptPoint, skip interceptors execution
            // var vip = PluginUtils.dontIntercept(PluginsRegistryImpl.getInstance(), exchange);
            var vip = PluginUtils.dontIntercept(handlingService);
            if (Arrays.stream(vip).anyMatch(interceptPoint::equals)) {
                next(exchange);
                return;
            }

            request = ServiceRequest.of(exchange, ServiceRequest.class);
            response = ServiceResponse.of(exchange, ServiceResponse.class);
        } else {
            request = ByteArrayProxyRequest.of(exchange);
            response = ByteArrayProxyResponse.of(exchange);
        }

        pluginsRegistry
                .getInterceptors()
                .stream()
                .filter(ri -> ri.isEnabled())
                .map(ri -> ri.getInstance())
                // IMPORTANT: An interceptor can intercept
                // - requests handled by a Service when its request and response
                //   types are equal to the ones declared by the Service
                // - request handled by a Proxy when its request and response
                //   are ByteArrayProxyRequest and ByteArrayProxyResponse
                .filter(ri
                        -> (handlingService == null
                && cachedRequestType(ri).equals(ByteArrayProxyRequest.type())
                && cachedResponseType(ri).equals(ByteArrayProxyResponse.type()))
                || (handlingService != null
                && cachedRequestType(ri).equals(cachedRequestType(handlingService))
                && cachedResponseType(ri).equals(cachedResponseType(handlingService))))
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
                        LOGGER.debug("Executing interceptor {} for {} on intercept point {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                interceptPoint);

                        ri.handle(request, response);
                    } catch (Exception ex) {
                        LOGGER.error("Error executing interceptor {} for {} on intercept point {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                interceptPoint,
                                ex);
                        Exchange.setInError(exchange);
                        LambdaUtils.throwsSneakyException(ex);
                    }
                });

        // If an interceptor sets the response as errored
        // stop processing the request and send the response
        // This happens AFTER_AUTH, otherwise not authenticated requests
        // might snoop information. For instance, a request to MongoService might
        // be able to check if a collection exists (this check is done by
        // BEFORE_AUTH interceptor CollectionPropsInjector)
        if (this.interceptPoint == InterceptPoint.REQUEST_AFTER_AUTH
                && Exchange.isInError(exchange)) {
            // if in error but no status code use 400 Bad Request
            if (response.getStatusCode() < 0) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }

            // if this is a service, handle OPTIONS
            // otherwise requests with bad content receive CORS errors
            if (handlingService != null && request.isOptions()) {
                handlingService.handleOptions(ServiceRequest.of(exchange,
                        ServiceRequest.class));
            }

            sender.handleRequest(exchange);
        } else {
            next(exchange);
        }
    }
}
