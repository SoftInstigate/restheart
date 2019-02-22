/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.handlers;

import io.uiam.plugins.PluginsRegistry;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResponseCommitListener;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConduitFactory;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.conduits.StreamSinkConduit;

/**
 * Executes the interceptors for proxied resource taking care of buffering the
 * response from the backend to make it accessible to them whose
 * requiresResponseContent() returns true
 *
 * Note that getting the content has significant performance overhead for
 * proxied resources. To mitigate DoS attacks the injector limits the size of
 * the content to ModificableContentSinkConduit.MAX_CONTENT_SIZE bytes
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseProxyInterceptorsHandler extends PipedHttpHandler {

    static final Logger LOGGER = LoggerFactory.getLogger(ResponseProxyInterceptorsHandler.class);

    public static final AttachmentKey<ModificableContentSinkConduit> MCSK_KEY
            = AttachmentKey.create(ModificableContentSinkConduit.class);

    /**
     * @param next
     */
    public ResponseProxyInterceptorsHandler() {
        super(null);
    }

    /**
     * @param next
     */
    public ResponseProxyInterceptorsHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // wrap the response buffering it if any interceptor resolvers the request 
        // and requires the content from the backend
        exchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(
                    ConduitFactory<StreamSinkConduit> factory,
                    HttpServerExchange exchange) {

                if (PluginsRegistry.getInstance()
                        .getResponseInterceptors()
                        .stream()
                        .filter(ri -> ri.resolve(exchange))
                        .anyMatch(ri -> ri.requiresResponseContent())) {
                    var mcsc = new ModificableContentSinkConduit(
                            factory.create(),
                            exchange);

                    exchange.putAttachment(MCSK_KEY, mcsc);

                    return mcsc;
                } else {
                    return factory.create();
                }
            }
        });

        // before sending the response execute the interceptors
        exchange.addResponseCommitListener(new ResponseCommitListener() {
            @Override
            public void beforeCommit(HttpServerExchange exchange) {
                PluginsRegistry.getInstance()
                        .getResponseInterceptors()
                        .stream()
                        .filter(ri -> ri.resolve(exchange))
                        .forEachOrdered(ri -> ri.handleRequest(exchange));
            }
        });

        // send the response to the client
        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange,
                    ExchangeCompletionListener.NextListener nextListener) {

                var mcsc = exchange.getAttachment(MCSK_KEY);

                if (mcsc != null) {
                    try {
                        mcsc.writeFinalToClient();
                    }
                    catch (IOException ex) {
                        LOGGER.error("error sending data to client", ex);
                    }
                }
            }
        });

        next(exchange);
    }
}
