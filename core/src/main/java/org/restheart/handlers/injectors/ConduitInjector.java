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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.handlers.ContentStreamSinkConduit;
import org.restheart.handlers.ModifiableContentSinkConduit;
import org.restheart.handlers.PipelinedHandler;
import static org.restheart.plugins.InterceptPoint.RESPONSE;
import static org.restheart.plugins.InterceptPoint.RESPONSE_ASYNC;

import org.restheart.plugins.Interceptor;
import org.restheart.plugins.PluginsRegistryImpl;
import static org.restheart.utils.PluginUtils.requiresContent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.xnio.conduits.StreamSinkConduit;

/**
 * Executes the interceptors for proxied requests taking care of buffering the
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

    public static final AttachmentKey<ModifiableContentSinkConduit> MCSC_KEY = AttachmentKey.create(ModifiableContentSinkConduit.class);

    public static final AttachmentKey<HeaderMap> ORIGINAL_ACCEPT_ENCODINGS_KEY = AttachmentKey.create(HeaderMap.class);

    @SuppressWarnings("rawtypes")
    private final List<Interceptor> inteceptors = new ArrayList<>();
    /**
     *
     */
    public ConduitInjector() {
        this(null);
    }

    /**
     * @param next
     */
    public ConduitInjector(PipelinedHandler next) {
        super(next);
        var registry = PluginsRegistryImpl.getInstance();
        this.inteceptors.addAll(registry.getProxyInterceptors(RESPONSE));
        this.inteceptors.addAll(registry.getProxyInterceptors(RESPONSE_ASYNC));
    }

    /**
     * if the ModificableContentSinkConduit is set, set the Accept-Encoding
     * header to identity this is required to avoid response interceptors
     * dealing with compressed data
     *
     * @param exchange
     */
    private void forceIdentityEncodingForInterceptors(HttpServerExchange exchange) {
        if (this.inteceptors.stream().anyMatch(ri -> requiresContent(ri))) {
            var before = new HeaderMap();

            if (exchange.getRequestHeaders().contains(Headers.ACCEPT_ENCODING)) {
                exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING).forEach((value) -> {
                    before.add(Headers.ACCEPT_ENCODING, value);
                });
            }

            exchange.putAttachment(ORIGINAL_ACCEPT_ENCODINGS_KEY, before);

            LOGGER.debug("{} setting encoding to identity because request involves response interceptors.", before);

            exchange.getRequestHeaders().put(Headers.ACCEPT_ENCODING, "identity");
        }
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // of the response buffering it if any interceptor resolvers the request
        // and requires the content from the backend
        exchange.addResponseWrapper((ConduitFactory<StreamSinkConduit> factory, HttpServerExchange cexchange) -> {
            // restore MDC context
            // MDC context is put in the thread context
            // For proxied requests a thread switch in the request handling happens,
            // loosing the MDC context. TracingInstrumentationHandler adds it to the
            // exchange as an Attachment
            var mdcCtx = ByteArrayProxyResponse.of(exchange).getMDCContext();
            if (mdcCtx != null) {
                MDC.setContextMap(mdcCtx);
            }

            if (this.inteceptors.stream().anyMatch(ri -> requiresContent(ri))) {
                var mcsc = new ModifiableContentSinkConduit(factory.create(), cexchange);
                cexchange.putAttachment(MCSC_KEY, mcsc);
                return mcsc;
            } else {
                return new ContentStreamSinkConduit(factory.create(), cexchange);
            }
        });

        forceIdentityEncodingForInterceptors(exchange);

        next(exchange);
    }
}
