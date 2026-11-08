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
import java.nio.ByteBuffer;
import java.util.Optional;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.exchange.PipelineInfo;
import org.restheart.exchange.Request;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Service;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.PluginsRegistryImpl;

/**
 *
 * Sends the response content to the client
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseSender extends PipelinedHandler {
    private final PluginsRegistry pluginsRegistry = PluginsRegistryImpl.getInstance();

    /**
     *
     */
    // the service of this pipeline, known when the pipeline is built; null to look it up per request
    private PluginRecord<Service<?, ?>> service = null;

    /**
     * @param service the service of the pipeline this handler belongs to
     * @return this handler
     * @since 9.15.0
     */
    public ResponseSender forService(PluginRecord<Service<?, ?>> service) {
        this.service = service;
        return this;
    }

    public ResponseSender() {
        super(null);
    }

    /**
     * @param next
     */
    public ResponseSender(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final PipelineInfo.PIPELINE_TYPE type;
        final Optional<PluginRecord<Service<?, ?>>> srv;

        if (service != null) {
            // the sender of a service pipeline knows its service
            type = PipelineInfo.PIPELINE_TYPE.SERVICE;
            srv = Optional.of(service);
        } else {
            var attached = Request.getPipelineInfo(exchange);
            final var pi = attached != null ? attached : pluginsRegistry.getPipelineInfo(exchange.getRequestPath());
            type = pi.getType();
            srv = type == PipelineInfo.PIPELINE_TYPE.SERVICE
                    ? pluginsRegistry.getServices().stream().filter(s -> s.getName().equals(pi.getName())).findAny()
                    : Optional.empty();
        }

        if (type == PipelineInfo.PIPELINE_TYPE.SERVICE) {

            if (srv.isPresent()) {
                var response = (ServiceResponse) srv.get().getInstance().response().apply(exchange);

                if (response.getStatusCode() > 0) {
                    exchange.setStatusCode(response.getStatusCode());
                }

                // send the content to the client
                if (response.getCustomSender() != null) {
                    // use the custom sender if it has been set
                    response.getCustomSender().run();
                } else {
                    var content = response.readContent();
                    if (content != null) {
                        // send the content via default exchange response sender
                        exchange.getResponseSender().send(content);
                    }
                }
            }
        } else if (type == PipelineInfo.PIPELINE_TYPE.PROXY) {
            var response = ByteArrayProxyResponse.of(exchange);

            if (!exchange.isResponseStarted() && response.getStatusCode() > 0) {
                exchange.setStatusCode(response.getStatusCode());
            }

            if (response.isContentAvailable()) {
                exchange.getResponseSender().send(ByteBuffer.wrap(response.readContent()));
            }

            exchange.endExchange();
        }

        next(exchange);
    }
}
