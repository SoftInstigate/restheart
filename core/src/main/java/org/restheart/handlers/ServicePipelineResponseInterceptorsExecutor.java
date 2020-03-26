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
import org.restheart.handlers.exchange.AbstractExchange;
import org.restheart.handlers.exchange.ByteArrayRequest;
import org.restheart.handlers.exchange.ByteArrayResponse;
import static org.restheart.handlers.exchange.PipelineBranchInfo.PIPELINE_BRANCH.SERVICE;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.PluginUtils.interceptPoint;
import static org.restheart.utils.PluginUtils.requiresContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * conduit that executes response interceptors that don't require response
 * content; response interceptors that require response content are executed by
 * ModifiableContentSinkConduit.terminateWrites()
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ServicePipelineResponseInterceptorsExecutor
        extends PipelinedHandler {

    static final Logger LOGGER = LoggerFactory
            .getLogger(ServicePipelineResponseInterceptorsExecutor.class);

    public ServicePipelineResponseInterceptorsExecutor() {
        this(null);
    }
    
    /**
     * Construct a new instance.
     *
     * @param next
     */
    public ServicePipelineResponseInterceptorsExecutor(PipelinedHandler next) {
        super(next);
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var pi = ByteArrayRequest.wrap(exchange).getPipelineBranchInfo();
        
        if (pi == null 
                || pi.getBranch() == null
                || pi.getBranch() != SERVICE) {
            throw new IllegalStateException("ResponseInterceptorsExecturor "
                    + "only supports service pipeline: " + pi);
        }
                
        if (!AbstractExchange.isInError(exchange)
                && !AbstractExchange.responseInterceptorsExecuted(exchange)) {
            AbstractExchange.setResponseInterceptorsExecuted(exchange);
            executeAsyncResponseInterceptor(exchange);
            executeResponseInterceptor(exchange);
        }
        
        next(exchange);
    }

    private void executeResponseInterceptor(HttpServerExchange exchange) {
        AbstractExchange.setResponseInterceptorsExecuted(exchange);
        PluginsRegistryImpl.getInstance()
                .getInterceptors()
                .stream()
                .filter(i -> interceptPoint(
                i.getInstance()) == InterceptPoint.RESPONSE)
                .filter(ri -> ri.isEnabled())
                .map(ri -> ri.getInstance())
                .filter(ri -> ri.resolve(exchange))
                .forEachOrdered(ri -> {
                    LOGGER.debug("Executing response interceptor {} for {}",
                            ri.getClass().getSimpleName(),
                            exchange.getRequestPath());

                    try {
                        ri.handle(exchange);
                    }
                    catch (Exception ex) {
                        LOGGER.error("Error executing response interceptor {} for {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath(),
                                ex);
                        AbstractExchange.setInError(exchange);
                        // set error message
                        ByteArrayResponse response = ByteArrayResponse
                                .wrap(exchange);

                        response.endExchangeWithMessage(
                                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                "Error executing response interceptor "
                                + ri.getClass().getSimpleName(),
                                ex);
                    }
                });
    }

    private void executeAsyncResponseInterceptor(HttpServerExchange exchange) {
        AbstractExchange.setResponseInterceptorsExecuted(exchange);
        PluginsRegistryImpl.getInstance()
                .getInterceptors()
                .stream()
                .filter(i -> interceptPoint(
                i.getInstance()) == InterceptPoint.RESPONSE_ASYNC)
                .filter(ri -> ri.isEnabled())
                .map(ri -> ri.getInstance())
                .filter(ri -> ri.resolve(exchange))
                // this conduit does not provide access to response content
                .filter(ri -> !requiresContent(ri))
                .forEachOrdered(ri -> {
                    exchange.getConnection().getWorker().execute(() -> {
                        LOGGER.debug("Executing async response interceptor {} for {}",
                                ri.getClass().getSimpleName(),
                                exchange.getRequestPath());

                        try {
                            ri.handle(exchange);
                        }
                        catch (Exception ex) {
                            LOGGER.error("Error executing response interceptor {} for {}",
                                    ri.getClass().getSimpleName(),
                                    exchange.getRequestPath(),
                                    ex);
                            AbstractExchange.setInError(exchange);
                            // set error message
                            ByteArrayResponse response = ByteArrayResponse
                                    .wrap(exchange);

                            response.endExchangeWithMessage(
                                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                    "Error executing response interceptor "
                                    + ri.getClass().getSimpleName(),
                                    ex);
                        }
                    });
                });
    }

}
