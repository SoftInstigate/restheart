/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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

import java.util.stream.Collectors;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.UninitializedRequest;
import static org.restheart.plugins.InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.LambdaUtils;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes the Interceptor with interceptPoint REQUEST_BEFORE_EXCHANGE_INIT
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BeforeExchangeInitInterceptorsExecutor extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BeforeExchangeInitInterceptorsExecutor.class);


    private final PluginsRegistry pluginsRegistry;

    /**
     *
     * @param interceptPoint
     */
    public BeforeExchangeInitInterceptorsExecutor() {
        super(null);
        this.pluginsRegistry = PluginsRegistryImpl.getInstance();
    }

    /**
     * @param next
     * @param interceptPoint
     */
    public BeforeExchangeInitInterceptorsExecutor(PipelinedHandler next) {
        super(next);
        this.pluginsRegistry = PluginsRegistryImpl.getInstance();
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    @SuppressWarnings({"unchecked"})
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var interceptors = pluginsRegistry.getInterceptors().stream()
            .filter(pr -> pr.isEnabled())
            .map(pr -> pr.getInstance())
            .filter(i -> PluginUtils.interceptPoint(i) == REQUEST_BEFORE_EXCHANGE_INIT)
            .filter(i -> i instanceof WildcardInterceptor)
            .collect(Collectors.toList());

        var request = new UninitializedRequest(exchange);

        interceptors.stream().filter(ri -> {
            try {
                return ri.resolve(request, null);
            } catch (Exception e) {
                LOGGER.warn("Error resolving interceptor {} for {} on intercept point {}", ri.getClass().getSimpleName(), exchange.getRequestPath(), REQUEST_BEFORE_EXCHANGE_INIT, e);

                return false;
            }})
            .forEachOrdered(ri -> {
                try {
                    LOGGER.debug("Executing interceptor {} for {} on intercept point {}", PluginUtils.name(ri), exchange.getRequestPath(), REQUEST_BEFORE_EXCHANGE_INIT);
                    ri.handle(request, null);
                } catch (Exception ex) {
                    LOGGER.error("Error executing interceptor {} for {} on intercept point {}", PluginUtils.name(ri), exchange.getRequestPath(), REQUEST_BEFORE_EXCHANGE_INIT, ex);

                    Exchange.setInError(exchange);
                    LambdaUtils.throwsSneakyException(ex);
                }
            });

        next(exchange);
    }
}
