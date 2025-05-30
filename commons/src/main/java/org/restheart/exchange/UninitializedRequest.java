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

import java.io.IOException;
import java.util.function.Consumer;

import org.restheart.utils.ChannelReader;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * ServiceRequest implementation that represents an uninitialized request state.
 * <p>
 * This class wraps the HTTP exchange and provides limited access to request attributes
 * before the full request initialization process has been completed. It serves as a
 * bridge between the raw HTTP exchange and the fully initialized service-specific
 * request objects, allowing early-stage interceptors to examine and modify requests.
 * </p>
 * <p>
 * UninitializedRequest is specifically designed for use by interceptors that operate
 * at the REQUEST_BEFORE_EXCHANGE_INIT intercept point, where the exchange exists but
 * service-specific initialization hasn't occurred yet. This enables preprocessing
 * of requests before they are committed to a specific service handler.
 * </p>
 * <p>
 * Key characteristics:
 * <ul>
 *   <li>Provides access to basic request attributes (path, headers, etc.)</li>
 *   <li>Content can only be accessed in raw byte format</li>
 *   <li>Supports custom request initialization delegation</li>
 *   <li>Prevents access to parsed content to maintain uninitialized state</li>
 *   <li>Allows modification of raw request body before initialization</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Usage Pattern:</strong> This class is typically used by security interceptors,
 * request logging mechanisms, or content preprocessing handlers that need to examine
 * or modify requests before they are processed by specific services.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class UninitializedRequest extends ServiceRequest<Object> {
    /** Attachment key for storing custom request initializer functions in the HTTP exchange. */
    static final AttachmentKey<Consumer<HttpServerExchange>> CUSTOM_REQUEST_INITIALIZER_KEY = AttachmentKey.create(Consumer.class);

    /**
     * Constructs a new UninitializedRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is private and should only be called by the factory method.
     * The request is created without being attached to the exchange (dontAttach=true)
     * to maintain its uninitialized state and prevent conflicts with later
     * service-specific request initialization.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    private UninitializedRequest(HttpServerExchange exchange) {
        super(exchange, true);
    }

    /**
     * Factory method to create an UninitializedRequest from an HTTP exchange.
     * <p>
     * This method creates a new UninitializedRequest instance that wraps the
     * exchange without performing any service-specific initialization. The
     * resulting request provides limited access appropriate for early-stage
     * request processing.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     * @return a new UninitializedRequest instance
     */
    public static UninitializedRequest of(HttpServerExchange exchange) {
        return new UninitializedRequest(exchange);
    }

    /**
     * Throws IllegalStateException as parsed content is not available in uninitialized state.
     * <p>
     * This method is intentionally disabled for UninitializedRequest to maintain
     * the uninitialized state contract. Content can only be retrieved in raw format
     * using {@link #getRawContent()} to prevent premature parsing and initialization.
     * </p>
     *
     * @return this method never returns normally
     * @throws IllegalStateException always thrown to indicate the request is not initialized
     */
    @Override
    public Object getContent() {
        throw new IllegalStateException("the request is not initialized");
    }

    /**
     * Throws IllegalStateException as parsed content cannot be set in uninitialized state.
     * <p>
     * This method is intentionally disabled for UninitializedRequest to maintain
     * the uninitialized state contract. Content can only be set in raw format
     * using {@link #setRawContent(byte[])} to prevent premature parsing and initialization.
     * </p>
     *
     * @param content the content that would be set (ignored)
     * @throws IllegalStateException always thrown to indicate the request is not initialized
     */
    @Override
    public void setContent(Object content) {
        throw new IllegalStateException("the request is not initialized");
    }

    /**
     * Returns the raw request body content as bytes.
     * <p>
     * This method provides access to the unprocessed request body content
     * without any parsing, transformation, or interpretation. It reads the
     * raw bytes directly from the HTTP exchange channel, making it suitable
     * for early-stage request inspection or modification.
     * </p>
     * <p>
     * This is the only way to access request content in the uninitialized state,
     * as it preserves the raw format without triggering service-specific parsing
     * or initialization processes.
     * </p>
     *
     * @return the request body as raw bytes, or an empty array if no content
     * @throws IllegalStateException if there is an error reading the raw request content
     */
    public byte[] getRawContent() {
        try {
            return ChannelReader.readBytes(wrapped);
        } catch (IOException e) {
            throw new IllegalStateException("error getting raw request content", e);
        }
    }

    /**
     * Overwrites the request body with the provided raw bytes.
     * <p>
     * This method allows modification of the request body content before
     * service-specific initialization occurs. It's particularly useful for
     * request preprocessing, content transformation, or security filtering
     * that needs to happen before the request is committed to a specific
     * service handler.
     * </p>
     * <p>
     * The method makes most sense when invoked before request initialization,
     * as it can affect how the content will be parsed and processed by the
     * target service. After initialization, content modification may not
     * have the intended effect.
     * </p>
     *
     * @param data the raw bytes to set as the new request body content
     * @throws IOException if there is an error writing the content to the request
     */
    public void setRawContent(byte[] data) throws IOException {
        ByteArrayProxyRequest.of(wrapped).writeContent(data);
    }

    /**
     * Sets a custom request initializer to override the default service initialization.
     * <p>
     * If a customRequestInitializer is set (not null), the ServiceExchangeInitializer will
     * delegate to customRequestInitializer.accept(exchange) the responsibility to initialize
     * the request instead of using the service's default initialization logic.
     * </p>
     * <p>
     * This mechanism allows interceptors to completely customize how requests are
     * initialized, enabling scenarios such as:
     * <ul>
     *   <li>Custom content parsing for specialized data formats</li>
     *   <li>Alternative request routing based on content inspection</li>
     *   <li>Security-driven request transformation</li>
     *   <li>Dynamic service selection based on request properties</li>
     * </ul>
     * </p>
     *
     * @param customRequestInitializer the custom initializer function to use for request initialization
     */
    public void setCustomRequestInitializer(Consumer<HttpServerExchange> customRequestInitializer) {
        this.wrapped.putAttachment(CUSTOM_REQUEST_INITIALIZER_KEY, customRequestInitializer);
    }

    /**
     * Returns the custom request initializer if one has been set.
     * <p>
     * This method retrieves the custom Consumer that was previously set via
     * {@link #setCustomRequestInitializer(Consumer)} and will be used to initialize
     * the request in place of the service's default initialization logic.
     * </p>
     *
     * @return the custom initializer function, or null if no custom initializer has been set
     */
    public Consumer<HttpServerExchange> customRequestInitializer() {
        return this.wrapped.getAttachment(CUSTOM_REQUEST_INITIALIZER_KEY);
    }

    /**
     * Throws IllegalStateException as content parsing is not available in uninitialized state.
     * <p>
     * This method is intentionally disabled for UninitializedRequest to maintain
     * the uninitialized state contract. Content parsing would trigger initialization,
     * which contradicts the purpose of this class.
     * </p>
     *
     * @return this method never returns normally
     * @throws IllegalStateException always thrown to indicate the request is not initialized
     */
    @Override
    public Object parseContent() throws IOException, BadRequestException {
        throw new IllegalStateException("the request is not initialized");
    }
}
