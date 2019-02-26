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
package io.uiam.handlers.injectors;

import io.uiam.handlers.ModificableContentSinkConduit;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.PluginsRegistry;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
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
public class ConduitInjector extends PipedHttpHandler {

    static final Logger LOGGER = LoggerFactory.getLogger(ConduitInjector.class);

    public static final AttachmentKey<ModificableContentSinkConduit> MCSC_KEY
            = AttachmentKey.create(ModificableContentSinkConduit.class);
    
    public static final AttachmentKey<HeaderMap> ORIGINAL_ACCEPT_ENCODINGS_KEY
            = AttachmentKey.create(HeaderMap.class);

    /**
     * @param next
     */
    public ConduitInjector(PipedHttpHandler next) {
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

                    exchange.putAttachment(MCSC_KEY, mcsc);

                    return mcsc;
                } else {
                    return factory.create();
                }
            }
        });
        
        forceIdentityEncodingForInterceptors(exchange);

        next(exchange);
    }

    /**
     * if the ModificableContentSinkConduit is set, set the Accept-Encoding
     * header to identity this is required to avoid response interceptors
     * dealing with compressed data
     *
     * @param exchange
     */
    private static void forceIdentityEncodingForInterceptors(HttpServerExchange exchange) {
        if (PluginsRegistry.getInstance()
                .getResponseInterceptors()
                .stream()
                .filter(ri -> ri.resolve(exchange))
                .anyMatch(ri -> ri.requiresResponseContent())) {
            var _before = exchange.getRequestHeaders()
                    .get(Headers.ACCEPT_ENCODING);

            var before = new HeaderMap();

            for (var value : _before) {
                before.add(Headers.ACCEPT_ENCODING, value);
            }
            
            exchange.putAttachment(ORIGINAL_ACCEPT_ENCODINGS_KEY, before);

            LOGGER.debug("{} "
                    + "setting it to identity because request involves "
                    + "response interceptors. "
                    + "Todo: compress response after interceptors execute", before);

            exchange.getRequestHeaders().put(
                    Headers.ACCEPT_ENCODING,
                    "identity");
        }
    }
}
