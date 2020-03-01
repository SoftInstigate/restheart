/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.security.handlers.ModifiableContentSinkConduit;
import org.restheart.security.handlers.ResponseInterceptorsStreamSinkConduit;
import org.restheart.security.plugins.PluginsRegistry;
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
public class ConduitInjector extends PipelinedHandler {

    static final Logger LOGGER = LoggerFactory.getLogger(ConduitInjector.class);

    public static final AttachmentKey<ModifiableContentSinkConduit> MCSC_KEY
            = AttachmentKey.create(ModifiableContentSinkConduit.class);

    public static final AttachmentKey<HeaderMap> ORIGINAL_ACCEPT_ENCODINGS_KEY
            = AttachmentKey.create(HeaderMap.class);

    /**
     * @param next
     */
    public ConduitInjector(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     */
    public ConduitInjector() {
        super();
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
        exchange.addResponseWrapper((ConduitFactory<StreamSinkConduit> factory,
                HttpServerExchange cexchange) -> {
            if (PluginsRegistry.getInstance()
                    .getResponseInterceptors()
                    .stream()
                    .filter(ri -> ri.isEnabled())
                    .filter((ri) -> ri.getInstance().resolve(cexchange))
                    .anyMatch(ri -> ri.getInstance()
                    .requiresResponseContent())) {
                var mcsc = new ModifiableContentSinkConduit(factory.create(), 
                        cexchange);
                cexchange.putAttachment(MCSC_KEY, mcsc);
                return mcsc;
            } else {
                return new ResponseInterceptorsStreamSinkConduit(factory.create(), 
                        cexchange);
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
    private static void forceIdentityEncodingForInterceptors(
            HttpServerExchange exchange) {
        if (PluginsRegistry.getInstance()
                .getResponseInterceptors()
                .stream()
                .filter(ri -> ri.isEnabled())
                .filter(ri -> ri.getInstance().resolve(exchange))
                .anyMatch(ri -> ri.getInstance().requiresResponseContent())) {
            var _before = exchange.getRequestHeaders()
                    .get(Headers.ACCEPT_ENCODING);

            var before = new HeaderMap();

            _before.forEach((value) -> {
                before.add(Headers.ACCEPT_ENCODING, value);
            });

            exchange.putAttachment(ORIGINAL_ACCEPT_ENCODINGS_KEY, before);

            LOGGER.debug("{} "
                    + "setting encoding to identity because request involves "
                    + "response interceptors.", before);

            exchange.getRequestHeaders().put(
                    Headers.ACCEPT_ENCODING,
                    "identity");
        }
    }
}
