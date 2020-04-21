/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
 * A response that stores content in a class field.
 *
 * Only one response can be instantiated per each exchange. The single object is
 * instantiated by ServiceExchangeInitializer using the responseInitializer()
 * function defined by the handling service
 *
 * Cannot be used to access content of a proxied resource, must use
 * BufferedResponse instead.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T>
 */
public abstract class Response<T> extends AbstractResponse<T> {
    private static final AttachmentKey<Response<?>> RESPONSE_KEY
            = AttachmentKey.create(Response.class);

    protected T content;

    protected Response(HttpServerExchange exchange) {
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

    @SuppressWarnings("unchecked")
    public static <R extends Response<?>> R of(HttpServerExchange exchange, Class<R> type) {
        var ret = exchange.getAttachment(RESPONSE_KEY);

        if (ret == null) {
            throw new IllegalStateException("Response not initialized");
        }

        if (type.isAssignableFrom(ret.getClass())) {
            return (R) ret;
        } else {
            throw new IllegalStateException("Response bound to exchange is not "
                    + "of the specified type,"
                    + " expected " + type.getClass().getSimpleName()
                    + " got" + ret.getClass().getSimpleName());
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
     *
     * @param code
     * @param message
     * @param t
     */
    @Override
    public abstract void setInError(int code, String message, Throwable t);
}
