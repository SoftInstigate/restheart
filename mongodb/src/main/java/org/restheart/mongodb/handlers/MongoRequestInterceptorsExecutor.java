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
package org.restheart.mongodb.handlers;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.AbstractExchange;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.BufferedByteArrayResponse;
import org.restheart.handlers.exchange.Request;
import org.restheart.handlers.exchange.Response;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.LambdaUtils;
import static org.restheart.utils.PluginUtils.interceptPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes after-auth interceptors for mongoService after the db and collection
 * metadata have been injected to the request
 *
 * It implements Initializer only to be able get pluginsRegistry via
 * InjectPluginsRegistry annotation
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
//@RegisterPlugin(name = "mongoRequestInterceptorsExecutor",
//        description = "executes after-auth interceptors for mongoService",
//        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class MongoRequestInterceptorsExecutor extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(MongoRequestInterceptorsExecutor.class);

    private PluginsRegistry registry;

    /**
     * @param registry
     */
    public MongoRequestInterceptorsExecutor(PluginsRegistry registry) {
        this(registry, null);
    }

    /**
     * @param registry
     * @param next
     */
    public MongoRequestInterceptorsExecutor(PluginsRegistry registry, PipelinedHandler next) {
        super(next);
        this.registry = registry;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        registry
                .getInterceptors()
                .stream()
                .filter(ri -> ri.isEnabled())
                .map(ri -> ri.getInstance())
                .filter(ri -> InterceptPoint.REQUEST_AFTER_AUTH == interceptPoint(ri))
                // IMPORTANT: An interceptor can intercep
                // - requests handled by MongoService when its request and response 
                //   types are BsonRequest and BsonResponse
                .filter(ri -> ri.requestType().equals(BsonRequest.type())
                && ri.responseType().equals(BsonResponse.type()))
                .filter(ri -> {
                    try {
                        return ri.resolve(Request.of(exchange), Response.of(exchange));
                    } catch (Exception e) {
                        LOGGER.warn("Error resolving interceptor {} for {} on intercept point REQUEST_AFTER_AUTH",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                e);

                        return false;
                    }
                })
                .forEachOrdered(ri -> {
                    try {
                        LOGGER.debug("Executing request interceptor {} for {} on intercept point REQUEST_AFTER_AUTH",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath());

                        ri.handle(Request.of(exchange), Response.of(exchange));
                    } catch (Exception ex) {
                        LOGGER.error("Error executing request interceptor {} for {} on intercept point REQUEST_AFTER_AUTH",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                ex);
                        AbstractExchange.setInError(exchange);
                        LambdaUtils.throwsSneakyExcpetion(ex);
                    }
                });

        // if an interceptor sets the response as errored
        // stop processing the request and send the response
        if (AbstractExchange.isInError(exchange)) {
            var response = BufferedByteArrayResponse.wrap(exchange);
            // if in error but no status code use 400 Bad Request
            if (response.getStatusCode() < 0) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }

        }

        next(exchange);
    }

    /**
     * does nothing, implements Initializer only to get pluginsRegistry
     */
//    @Override
//    public void init() {
//    }
}
