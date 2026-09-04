/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.exchange;

import io.undertow.server.HttpServerExchange;

/**
 * {@link ServiceRequest} implementation used to run {@code WildcardInterceptor}s
 * on the SSE handshake pipeline (see {@code PluginsRegistryImpl#plugSseService}).
 *
 * <p>{@code SseService} is a {@code Plugin}, not an {@code ExchangeTypeResolver}:
 * it declares no request/response types of its own, so there is no
 * service-specific {@code ServiceRequest} subtype to attach to the exchange the
 * way {@code ServiceExchangeInitializer} does for a {@code Service}. This class
 * fills that gap, wrapping the exchange with just enough of the
 * {@code ServiceRequest} contract (path, headers, query parameters,
 * authenticated account) for a {@code WildcardInterceptor} to inspect and
 * modify, without pretending the SSE handshake has a parseable body.
 *
 * <p>The instance is never attached to the exchange under {@code ServiceRequest}'s own
 * {@code REQUEST_KEY}, so several can be created for the same exchange without conflicting
 * with other handlers that look the request up that way. The SSE pipeline runs interceptors at
 * both {@code REQUEST_BEFORE_AUTH} and {@code REQUEST_AFTER_AUTH}, via two invocations of
 * {@code SseWildcardInterceptorsExecutor}, which itself keeps the two invocations on the same
 * instance for a given exchange (under its own, dedicated attachment key) so that state set on
 * the request before auth is still visible after auth.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 * @see org.restheart.plugins.WildcardInterceptor
 * @see SseHandshakeResponse
 */
public class SseHandshakeRequest extends ServiceRequest<Object> {

    private SseHandshakeRequest(HttpServerExchange exchange) {
        super(exchange, true);
    }

    /**
     * Factory method to create an {@code SseHandshakeRequest} wrapping the given
     * HTTP exchange.
     *
     * @param exchange the HTTP server exchange to wrap
     * @return a new {@code SseHandshakeRequest} instance
     */
    public static SseHandshakeRequest of(HttpServerExchange exchange) {
        return new SseHandshakeRequest(exchange);
    }

    /**
     * Throws {@link IllegalStateException}: an SSE handshake request has no
     * parseable content.
     *
     * @return this method never returns normally
     */
    @Override
    public Object getContent() {
        throw new IllegalStateException("the SSE handshake request has no content");
    }

    /**
     * Throws {@link IllegalStateException}: an SSE handshake request has no
     * parseable content.
     *
     * @param content the content that would be set (ignored)
     */
    @Override
    public void setContent(Object content) {
        throw new IllegalStateException("the SSE handshake request has no content");
    }

    /**
     * Throws {@link IllegalStateException}: an SSE handshake request has no
     * parseable content.
     *
     * @return this method never returns normally
     */
    @Override
    public Object parseContent() {
        throw new IllegalStateException("the SSE handshake request has no content");
    }
}
