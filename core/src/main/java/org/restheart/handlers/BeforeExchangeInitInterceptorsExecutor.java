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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.UninitializedRequest;
import org.restheart.exchange.UninitializedResponse;
import org.restheart.plugins.InterceptorException;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.restheart.plugins.InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT;

/**
 * Executes the Interceptor with interceptPoint REQUEST_BEFORE_EXCHANGE_INIT
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BeforeExchangeInitInterceptorsExecutor extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BeforeExchangeInitInterceptorsExecutor.class);

    private ArrayList<WildcardInterceptor> wildCardInterceptors = new ArrayList<>();

    private final PluginsRegistry pluginsRegistry = PluginsRegistryImpl.getInstance();;

    /**
     *
     * @param interceptPoint
     */
    public BeforeExchangeInitInterceptorsExecutor() {
        this(null);
    }

    /**
     * @param next
     * @param interceptPoint
     */
    public BeforeExchangeInitInterceptorsExecutor(PipelinedHandler next) {
        super(next);
        this.wildCardInterceptors = pluginsRegistry.getInterceptors().stream()
            .filter(pr -> pr.isEnabled())
            .map(pr -> pr.getInstance())
            .filter(i -> PluginUtils.interceptPoint(i) == REQUEST_BEFORE_EXCHANGE_INIT)
            .filter(i -> i instanceof WildcardInterceptor)
            .map(i -> (WildcardInterceptor) i)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (this.wildCardInterceptors.isEmpty()) {
            next(exchange);
            return;
        }

        var request = UninitializedRequest.of(exchange);
        var response = UninitializedResponse.of(exchange);

        var handlingPlugin = PluginUtils.handlingService(pluginsRegistry, exchange);

        if (handlingPlugin != null) {
            // if the request is handled by a service set to not execute intrceptors
            // at this interceptPoint, skip interceptors execution
            var vip = PluginUtils.dontIntercept(handlingPlugin);
            if (Arrays.stream(vip).anyMatch(REQUEST_BEFORE_EXCHANGE_INIT::equals)) {
                next(exchange);
                return;
            }
        }

        this.wildCardInterceptors.stream().filter(ri -> {
            try {
                return ri.resolve(request, response);
            } catch (Exception ex) {
                LOGGER.warn("Error resolving interceptor {} for {} on intercept point {}", ri.getClass().getSimpleName(), exchange.getRequestPath(), REQUEST_BEFORE_EXCHANGE_INIT, ex);

                Exchange.setInError(exchange);
                LambdaUtils.throwsSneakyException(new InterceptorException("Error resolving interceptor " + ri.getClass().getSimpleName(), ex));
                return false;
            }})
            .forEachOrdered(ri -> {
                try {
                    LOGGER.debug("Executing interceptor {} for {} on intercept point {}", PluginUtils.name(ri), exchange.getRequestPath(), REQUEST_BEFORE_EXCHANGE_INIT);
                    ri.handle(request, response);
                } catch (Exception ex) {
                    LOGGER.error("Error executing interceptor {} for {} on intercept point {}", PluginUtils.name(ri), exchange.getRequestPath(), REQUEST_BEFORE_EXCHANGE_INIT, ex);

                    Exchange.setInError(exchange);
                    LambdaUtils.throwsSneakyException(new InterceptorException("Error executing interceptor " + ri.getClass().getSimpleName(), ex));
                }
            });

        next(exchange);
    }
}
