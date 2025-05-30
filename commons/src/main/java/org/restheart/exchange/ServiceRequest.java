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

import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Base class for Request implementations that handle service-based HTTP requests.
 * <p>
 * This abstract class extends Request to provide specialized functionality for requests
 * that are processed by RESTHeart services (as opposed to proxy or static resource handlers).
 * It manages content parsing, injection, and attachment to the HTTP exchange with strict
 * lifecycle management to ensure only one request object exists per exchange.
 * </p>
 * <p>
 * ServiceRequest is the foundation for all service-specific request types including:
 * <ul>
 *   <li>MongoRequest for MongoDB operations</li>
 *   <li>GraphQLRequest for GraphQL operations</li>
 *   <li>BsonRequest for BSON content handling</li>
 *   <li>JsonRequest for JSON content handling</li>
 *   <li>Custom service requests for specific business logic</li>
 * </ul>
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Automatic content parsing and injection on first access</li>
 *   <li>Single instance per exchange enforcement</li>
 *   <li>Error handling and response integration</li>
 *   <li>Service identification and routing support</li>
 *   <li>Content injection state management</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Lifecycle:</strong> The request object is instantiated by ServiceExchangeInitializer
 * using the requestInitializer() function defined by the handling service. Only one request
 * object can be instantiated per exchange to maintain consistency and prevent conflicts.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> the type of content handled by this service request
 */
public abstract class ServiceRequest<T> extends Request<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRequest.class);
    
    /** Attachment key for storing the service request instance in the HTTP exchange. */
    private static final AttachmentKey<ServiceRequest<?>> REQUEST_KEY = AttachmentKey.create(ServiceRequest.class);

    /** The parsed content of the request. */
    protected T content;

    /**
     * Constructs a new ServiceRequest and attaches it to the HTTP exchange.
     * <p>
     * This constructor creates a service request and automatically attaches it
     * to the exchange using the internal REQUEST_KEY. This ensures that the
     * request can be retrieved later using the static factory methods.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap and attach to
     * @throws IllegalStateException if another ServiceRequest is already attached to the exchange
     */
    protected ServiceRequest(HttpServerExchange exchange) {
        this(exchange, false);
    }

    /**
     * Constructs a new ServiceRequest with optional exchange attachment.
     * <p>
     * This constructor provides control over whether the request instance is attached
     * to the exchange. When attached (dontAttach=false), the request can be retrieved
     * using static factory methods. When not attached (dontAttach=true), the request
     * exists independently and must be managed manually.
     * </p>
     * <p>
     * An initialized request is normally attached to the exchange using the REQUEST_KEY
     * to enable retrieval and ensure single-instance semantics per exchange.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     * @param dontAttach true if the request should not be attached to the exchange, false otherwise
     * @throws IllegalStateException if dontAttach is false and another ServiceRequest is already attached
     */
    ServiceRequest(HttpServerExchange exchange, boolean dontAttach) {
        super(exchange);
        setContentInjected(false);

        if (!dontAttach) {
            if (exchange.getAttachment(REQUEST_KEY) != null) {
                throw new IllegalStateException("Error instantiating request object "
                    + getClass().getSimpleName()
                    + ", "
                    + exchange.getAttachment(REQUEST_KEY).getClass().getSimpleName()
                    + " already bound to the exchange");
            }

            exchange.putAttachment(REQUEST_KEY, this);
        }
    }

    /**
     * Retrieves the ServiceRequest instance attached to the given HTTP exchange.
     * <p>
     * This factory method returns the ServiceRequest that was previously attached
     * to the exchange during initialization. It ensures that service handlers can
     * access the request object without needing to pass it explicitly through
     * the processing pipeline.
     * </p>
     *
     * @param exchange the HTTP server exchange to retrieve the request from
     * @return the ServiceRequest attached to the exchange
     * @throws IllegalStateException if no ServiceRequest has been attached to the exchange
     */
    public static ServiceRequest<?> of(HttpServerExchange exchange) {
        var ret = exchange.getAttachment(REQUEST_KEY);

        if (ret == null) {
            throw new IllegalStateException("Request not initialized");
        }

        return ret;
    }

    /**
     * Retrieves the ServiceRequest instance attached to the exchange, cast to the specified type.
     * <p>
     * This type-safe factory method returns the ServiceRequest attached to the exchange
     * and casts it to the requested type. It provides compile-time type safety while
     * performing runtime type checking to ensure the attached request is of the expected type.
     * </p>
     *
     * @param <R> the expected ServiceRequest subtype
     * @param exchange the HTTP server exchange to retrieve the request from
     * @param type the expected class type of the ServiceRequest
     * @return the ServiceRequest attached to the exchange, cast to type R
     * @throws IllegalStateException if no ServiceRequest is attached or if the attached request is not of the expected type
     */
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

    /**
     * Retrieves the content of the request, triggering parsing if not already done.
     * <p>
     * This method implements lazy content loading by checking if content has been previously
     * injected. If not, it invokes {@link #parseContent()} to parse the request body and
     * attach the content to the request. This approach optimizes performance by only parsing
     * content when it's actually needed.
     * </p>
     * <p>
     * The method handles errors during content parsing by:
     * <ul>
     *   <li>Marking the request as errored</li>
     *   <li>Setting appropriate error responses</li>
     *   <li>Throwing BadRequestException for client errors</li>
     *   <li>Wrapping IOException in RuntimeException for server errors</li>
     * </ul>
     * </p>
     *
     * @return the content of the request, which may be newly parsed or previously retrieved
     * @throws BadRequestException if the content cannot be parsed due to client error (malformed content, etc.)
     * @throws RuntimeException if an IOException occurs during content reading (wrapped for convenience)
     */
    public T getContent() throws BadRequestException {
        if (!isContentInjected()) {
            LOGGER.trace("getContent() called but content has not been injected yet. Let's inject it.");

            try {
                setContent(parseContent());
            } catch(BadRequestException bre) {
                this.setInError(true);
                Response.of(wrapped).setInError(bre.getStatusCode(), bre.getMessage(), bre);
                throw bre;
            } catch(IOException ioe) {
                if (!isInError()) { // parseContent() might have already marked the request as errored
                    this.setInError(true);
                    Response.of(wrapped).setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "error reading request content", ioe);
                }
                // wrap ioe in unchecked exception
                throw new RuntimeException(ioe);
            }
        }

        return this.content;
    }

    /**
     * Sets the content of the request and marks content as injected.
     * <p>
     * This method allows manual setting of request content, typically used when
     * content has been parsed externally or when providing mock content for testing.
     * It automatically sets the content injection flag to prevent redundant parsing.
     * </p>
     *
     * @param content the content to set for this request
     */
    public void setContent(T content) {
        this.content = content;
        setContentInjected(true);
    }

    /**
     * Parses the content from the exchange and converts it into an instance of the specified type T.
     * <p>
     * This abstract method must be implemented by subclasses to handle the specific parsing
     * logic for their content type. The method retrieves data from the HTTP exchange,
     * interprets it according to the expected format, and converts it into an object of type T.
     * </p>
     * <p>
     * Common parsing operations include:
     * <ul>
     *   <li>Reading raw bytes from the request body</li>
     *   <li>Converting character encoding (typically UTF-8)</li>
     *   <li>Parsing structured data (JSON, BSON, XML, etc.)</li>
     *   <li>Validating content format and structure</li>
     *   <li>Handling multipart or form-encoded data</li>
     * </ul>
     * </p>
     * <p>
     * Implementations should throw appropriate exceptions to indicate parsing failures,
     * allowing the framework to generate proper HTTP error responses for clients.
     * </p>
     *
     * @return an instance of T representing the parsed content
     * @throws IOException if an IO error occurs while reading the request body
     * @throws BadRequestException if the content does not match the expected format or is malformed
     */
    public abstract T parseContent() throws IOException, BadRequestException;

    /**
     * Checks if this request is being handled by the specified service.
     * <p>
     * This method compares the provided service name with the name of the service
     * that is currently handling this request, as determined by the pipeline
     * information. It's useful for conditional processing based on the handling service.
     * </p>
     *
     * @param serviceName the name of the service to check against
     * @return true if the request is handled by the specified service, false otherwise
     */
    public boolean isHandledBy(String serviceName) {
        return serviceName == null
            ? false
            : serviceName.equals(getPipelineInfo().getName());
    }

    /**
     * Attachment key for tracking whether request content has been injected.
     * <p>
     * CONTENT_INJECTED is true if the request body has been already injected/parsed.
     * Calling setContent() and setFileInputStream() sets CONTENT_INJECTED to true.
     * </p>
     * <p>
     * Calling getContent() or getFileInputStream() when CONTENT_INJECTED=false
     * triggers content injection via MongoRequestContentInjector or similar mechanisms.
     * This lazy loading approach optimizes performance by only parsing content when needed.
     * </p>
     */
    public static final AttachmentKey<Boolean> CONTENT_INJECTED = AttachmentKey.create(Boolean.class);

    /**
     * Checks whether content has been injected/parsed for this request.
     * <p>
     * This method returns the current state of content injection, indicating
     * whether the request body has been read and parsed into the content object.
     * It's used internally to implement lazy content loading.
     * </p>
     *
     * @return true if content has been injected, false if content parsing is still needed
     */
    public final boolean isContentInjected() {
        return this.wrapped.getAttachment(CONTENT_INJECTED);
    }

    /**
     * Sets the content injection state for this request.
     * <p>
     * This method updates the content injection flag to indicate whether content
     * has been parsed and is available. It's called internally when content is
     * set or parsed to prevent redundant parsing operations.
     * </p>
     *
     * @param value true to mark content as injected, false to mark as not injected
     */
    public final void setContentInjected(boolean value) {
        this.wrapped.putAttachment(CONTENT_INJECTED, value);
    }
}
