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
 * ServiceResponse implementation that represents an uninitialized response state.
 * <p>
 * This class wraps the HTTP exchange and provides limited response functionality
 * before the full response initialization process has been completed. It serves as a
 * bridge between the raw HTTP exchange and the fully initialized service-specific
 * response objects, allowing early-stage interceptors to configure response initialization.
 * </p>
 * <p>
 * UninitializedResponse is specifically designed for use by interceptors that operate
 * at the REQUEST_BEFORE_EXCHANGE_INIT intercept point, where the exchange exists but
 * service-specific initialization hasn't occurred yet. This enables preprocessing
 * of response configuration before it is committed to a specific service handler.
 * </p>
 * <p>
 * Key characteristics:
 * <ul>
 *   <li>Provides access to basic response attributes (headers, status, etc.)</li>
 *   <li>Content cannot be accessed or set to maintain uninitialized state</li>
 *   <li>Supports custom response initialization delegation</li>
 *   <li>Prevents premature response operations to maintain consistency</li>
 *   <li>Allows configuration of alternative response initialization logic</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Usage Pattern:</strong> This class is typically used by security interceptors,
 * response preprocessing handlers, or custom routing mechanisms that need to configure
 * how responses will be initialized based on request analysis.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class UninitializedResponse extends ServiceResponse<Object> {
    /** Attachment key for storing custom response initializer functions in the HTTP exchange. */
    static final AttachmentKey<Consumer<HttpServerExchange>> CUSTOM_RESPONSE_INITIALIZER_KEY = AttachmentKey.create(Consumer.class);

    /**
     * Constructs a new UninitializedResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is private and should only be called by the factory method.
     * The response is created without being attached to the exchange (dontAttach=true)
     * to maintain its uninitialized state and prevent conflicts with later
     * service-specific response initialization.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    private UninitializedResponse(HttpServerExchange exchange) {
        super(exchange, true);
    }

    /**
     * Factory method to create an UninitializedResponse from an HTTP exchange.
     * <p>
     * This method creates a new UninitializedResponse instance that wraps the
     * exchange without performing any service-specific initialization. The
     * resulting response provides limited access appropriate for early-stage
     * response configuration.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     * @return a new UninitializedResponse instance
     */
    public static UninitializedResponse of(HttpServerExchange exchange) {
        return new UninitializedResponse(exchange);
    }

    /**
     * Throws IllegalStateException as content is not available in uninitialized state.
     * <p>
     * This method is intentionally disabled for UninitializedResponse to maintain
     * the uninitialized state contract. Content operations are not allowed before
     * proper service-specific response initialization has occurred.
     * </p>
     *
     * @return this method never returns normally
     * @throws IllegalStateException always thrown to indicate the response is not initialized
     */
    @Override
    public Object getContent() {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * Throws IllegalStateException as content cannot be set in uninitialized state.
     * <p>
     * This method is intentionally disabled for UninitializedResponse to maintain
     * the uninitialized state contract. Content operations are not allowed before
     * proper service-specific response initialization has occurred.
     * </p>
     *
     * @param content the content that would be set (ignored)
     * @throws IllegalStateException always thrown to indicate the response is not initialized
     */
    @Override
    public void setContent(Object content) {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * Throws IllegalStateException as content reading is not available in uninitialized state.
     * <p>
     * This method is intentionally disabled for UninitializedResponse to maintain
     * the uninitialized state contract. Content operations are not allowed before
     * proper service-specific response initialization has occurred.
     * </p>
     *
     * @return this method never returns normally
     * @throws IllegalStateException always thrown to indicate the response is not initialized
     */
    @Override
    public String readContent() {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * Throws IllegalStateException as error handling is not available in uninitialized state.
     * <p>
     * This method is intentionally disabled for UninitializedResponse to maintain
     * the uninitialized state contract. Error response operations are not allowed
     * before proper service-specific response initialization has occurred.
     * </p>
     *
     * @param code the HTTP status code (ignored)
     * @param message the error message (ignored)
     * @param t the throwable (ignored)
     * @throws IllegalStateException always thrown to indicate the response is not initialized
     */
    @Override
    public void setInError(int code, String message, Throwable t) {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * Sets a custom response initializer to override the default service initialization.
     * <p>
     * If a customResponseInitializer is set (not null), the ServiceExchangeInitializer will
     * delegate to customResponseInitializer.accept(exchange) the responsibility to initialize
     * the response instead of using the service's default initialization logic.
     * </p>
     * <p>
     * This mechanism allows interceptors to completely customize how responses are
     * initialized, enabling scenarios such as:
     * <ul>
     *   <li>Custom response format selection based on request analysis</li>
     *   <li>Alternative response routing based on content negotiation</li>
     *   <li>Security-driven response configuration</li>
     *   <li>Dynamic service selection based on response requirements</li>
     * </ul>
     * </p>
     *
     * @param customResponseInitializer the custom initializer function to use for response initialization
     */
    public void setCustomResponseInitializer(Consumer<HttpServerExchange> customResponseInitializer) {
        this.wrapped.putAttachment(CUSTOM_RESPONSE_INITIALIZER_KEY, customResponseInitializer);
    }

    /**
     * Returns the custom response initializer if one has been set.
     * <p>
     * This method retrieves the custom Consumer that was previously set via
     * {@link #setCustomResponseInitializer(Consumer)} and will be used to initialize
     * the response in place of the service's default initialization logic.
     * </p>
     *
     * @return the custom initializer function, or null if no custom initializer has been set
     */
    public Consumer<HttpServerExchange> customResponseInitializer() {
        return this.wrapped.getAttachment(CUSTOM_RESPONSE_INITIALIZER_KEY);
    }
}
