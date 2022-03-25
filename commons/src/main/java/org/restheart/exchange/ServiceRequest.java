/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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
import io.undertow.util.AttachmentKey;

/**
 * Base class for Request implementations that can be used in service requests.
 *
 * Only one request object can be instantiated per exchage. The request object
 * is instantiated by ServiceExchangeInitializer using the requestInitializer()
 * function defined by the handling service
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> generic type
 */
public abstract class ServiceRequest<T> extends Request<T> {
    private static final AttachmentKey<ServiceRequest<?>> REQUEST_KEY = AttachmentKey.create(ServiceRequest.class);

    protected T content;

    protected ServiceRequest(HttpServerExchange exchange) {
        super(exchange);

        if (exchange.getAttachment(REQUEST_KEY) != null) {
            throw new IllegalStateException("Error instantiating request object "
                    + getClass().getSimpleName()
                    + ", "
                    + exchange.getAttachment(REQUEST_KEY).getClass().getSimpleName()
                    + " already bound to the exchange");
        }

        exchange.putAttachment(REQUEST_KEY, this);
    }

    public static ServiceRequest<?> of(HttpServerExchange exchange) {
        var ret = exchange.getAttachment(REQUEST_KEY);

        if (ret == null) {
            throw new IllegalStateException("Request not initialized");
        }

        return ret;
    }

    @SuppressWarnings("unchecked")
    public static <R extends ServiceRequest<?>> R of(HttpServerExchange exchange, Class<R> type) {
        var ret = exchange.getAttachment(REQUEST_KEY);

        if (ret == null) {
            throw new IllegalStateException("Request not initialized");
        }

        if (type.isAssignableFrom(ret.getClass())) {
            return (R) ret;
        } else {
            throw new IllegalStateException("Request bound to exchange is not "
                    + "of the specified type,"
                    + " expected " + type.getSimpleName()
                    + " got " + ret.getClass().getSimpleName());
        }
    }

    public T getContent() {
        return this.content;
    }

    public void setContent(T content) {
        this.content = content;
    }

    /**
     *
     * @param serviceName
     * @return true if the request is handled by the specified Service
     */
    public boolean isHandledBy(String serviceName) {
        return serviceName == null
            ? false
            : serviceName.equals(getPipelineInfo().getName());
    }
}
