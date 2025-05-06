/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

import java.util.function.Consumer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * UninitializedResponse wraps the exchage and allows
 * setting a customResponseInitializer
 *
 * Interceptors at intercePoint REQUEST_BEFORE_EXCHANGE_INIT
 * receive UninitializedResponse as response argument
 *
 */
public class UninitializedResponse extends ServiceResponse<Object> {
    static final AttachmentKey<Consumer<HttpServerExchange>> CUSTOM_RESPONSE_INITIALIZER_KEY = AttachmentKey.create(Consumer.class);

    private UninitializedResponse(HttpServerExchange exchange) {
        super(exchange, true);
    }

    public static UninitializedResponse of(HttpServerExchange exchange) {
        return new UninitializedResponse(exchange);
    }

    /**
     * throws IllegalStateException
     *
     */
    @Override
    public Object getContent() {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * throws IllegalStateException
     *
     */
    @Override
    public void setContent(Object content) {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * throws IllegalStateException
     *
     */
    @Override
    public String readContent() {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * throws IllegalStateException
     *
     */
    @Override
    public void setInError(int code, String message, Throwable t) {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * If a customResponseInitializer is set (not null), the ServiceExchangeInitializer will
     * delegate to customResponseInitializer.accept(exchange) the responsability to initialize the response
     *
     * @param customSender
     */
    public void setCustomResponseInitializer(Consumer<HttpServerExchange> customResponseInitializer) {
        this.wrapped.putAttachment(CUSTOM_RESPONSE_INITIALIZER_KEY, customResponseInitializer);
    }

    /**
     *
     * @return the custom Consumer used to initialize the response in place of the Service default one
     */
    public Consumer<HttpServerExchange> customResponseInitializer() {
        return this.wrapped.getAttachment(CUSTOM_RESPONSE_INITIALIZER_KEY);
    }
}
