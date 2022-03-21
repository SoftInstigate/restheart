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
 * Base class for Response implementations that can be used in service requests.
 *
 * Only one response object can be instantiated per request. The response object
 * is instantiated by ServiceExchangeInitializer using the responseInitializer()
 * function defined by the handling service
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> generic type
 */
public abstract class ServiceResponse<T> extends Response<T> {
    private static final AttachmentKey<ServiceResponse<?>> RESPONSE_KEY
            = AttachmentKey.create(ServiceResponse.class);

    protected T content;
    private Runnable customSender = null;

    protected ServiceResponse(HttpServerExchange exchange) {
        super(exchange);

        if (exchange.getAttachment(RESPONSE_KEY) != null) {
            throw new IllegalStateException("Error instantiating response object "
                    + getClass().getSimpleName()
                    + ", "
                    + exchange.getAttachment(RESPONSE_KEY).getClass().getSimpleName()
                    + " already bound to the exchange");
        }

        exchange.putAttachment(RESPONSE_KEY, this);
    }

    public static ServiceResponse<?> of(HttpServerExchange exchange) {
        var ret = exchange.getAttachment(RESPONSE_KEY);

        if (ret == null) {
            throw new IllegalStateException("Response not initialized");
        }

        return ret;
    }

    @SuppressWarnings("unchecked")
    public static <R extends ServiceResponse<?>> R of(HttpServerExchange exchange, Class<R> type) {
        var ret = exchange.getAttachment(RESPONSE_KEY);

        if (ret == null) {
            throw new IllegalStateException("Response not initialized");
        }

        if (type.isAssignableFrom(ret.getClass())) {
            return (R) ret;
        } else {
            throw new IllegalStateException("Response bound to exchange is not "
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
     * Reads the content as a String. This method is used by ResponseSender to
     * generate the response content to send to the client.
     *
     * @return the content as string
     */
    public abstract String readContent();

    /**
     * If a customSender is set (not null), the handler ResponseSender will
     * delegate to customSender.run() the responsability to send the response
     * content to the client
     *
     * @param customSender
     */
    public void setCustomSender(Runnable customSender) {
        this.customSender = customSender;
    }

    /**
     * @see setCustomerSender()
     * @return the customSender
     *
     */
    public Runnable getCustomSender() {
        return this.customSender;
    }

    /**
     * If a customSender is set (not null), the handler ResponseSender will
     * delegate to customSender.run() the responsability to send the response
     * content to the client
     *
     * @deprecated
     * @param customSender
     */
    public void setCustomerSender(Runnable customSender) {
        this.customSender = customSender;
    }



    /**
     * @see setCustomSender()
     * @deprecated
     * @return the customSender
     *
     */
    public Runnable getCustomerSender() {
        return this.customSender;
    }

    /**
     *
     * @param code
     * @param message
     * @param t
     */
    @Override
    public abstract void setInError(int code, String message, Throwable t);
}
