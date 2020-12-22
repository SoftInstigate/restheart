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
import java.nio.ByteBuffer;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.exchange.PipelineInfo;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.PluginsRegistryImpl;

/**
 *
 * Sends the response content to the client
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseSender extends PipelinedHandler {

    /**
     *
     */
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
    @SuppressWarnings({"unchecked","rawtypes"})
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var registry = PluginsRegistryImpl.getInstance();
        var path = exchange.getRequestPath();

        var pi = registry.getPipelineInfo(path);

        if (pi.getType() == PipelineInfo.PIPELINE_TYPE.SERVICE) {
            var srv = registry.getServices().stream()
                    .filter(s -> s.getName().equals(pi.getName()))
                    .findAny();

            if (srv.isPresent()) {
                var response = (ServiceResponse) srv.get().getInstance()
                        .response().apply(exchange);

                if (response.getStatusCode() > 0) {
                    exchange.setStatusCode(response.getStatusCode());
                }

                // send the content to the client
                if (response.getCustomerSender() != null) {
                    // use the custom sender if it has been set
                    response.getCustomerSender().run();
                } else if (response.readContent() != null) {
                    // send the content via default exchange response sender
                    exchange.getResponseSender().send(response.readContent());
                }
            }

        } else if (pi.getType() == PipelineInfo.PIPELINE_TYPE.PROXY) {
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
