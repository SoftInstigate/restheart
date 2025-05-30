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

import java.util.Map;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * The root class for implementing HTTP responses in the RESTHeart framework.
 * <p>
 * This abstract class provides a foundation for all response implementations by wrapping
 * Undertow's HttpServerExchange and offering simplified access to response elements
 * such as headers, status codes, and content. It serves as the base class for all
 * specific response types in RESTHeart.
 * </p>
 * <p>
 * The Response class manages:
 * <ul>
 *   <li>HTTP status codes and response headers</li>
 *   <li>Content type management with convenient methods</li>
 *   <li>Error response handling and formatting</li>
 *   <li>MDC (Mapped Diagnostic Context) for logging across thread boundaries</li>
 *   <li>Integration with RESTHeart's pipeline system</li>
 * </ul>
 * </p>
 * <p>
 * Response instances are automatically created based on the pipeline type:
 * <ul>
 *   <li>ServiceResponse for SERVICE pipelines (MongoDB, GraphQL, custom services)</li>
 *   <li>ByteArrayProxyResponse for PROXY and STATIC_RESOURCE pipelines</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> the type of content handled by this response
 */
public abstract class Response<T> extends Exchange<T> {
    /** Attachment key for storing the HTTP status code in the exchange. */
    private static final AttachmentKey<Integer> STATUS_CODE = AttachmentKey.create(Integer.class);

    /** Attachment key for storing the MDC logging context in the exchange. */
    private static final AttachmentKey<Map<String, String>> MDC_CONTEXT_KEY = AttachmentKey.create(Map.class);

    /**
     * Constructs a new Response wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by subclasses.
     * Use the appropriate factory methods to create specific response types.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected Response(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create an appropriate Response instance based on the pipeline type.
     * <p>
     * This method examines the pipeline information attached to the exchange and creates
     * the most suitable response type:
     * <ul>
     *   <li>ServiceResponse for SERVICE pipelines</li>
     *   <li>ByteArrayProxyResponse for PROXY and STATIC_RESOURCE pipelines</li>
     * </ul>
     * </p>
     *
     * @param exchange the HTTP server exchange containing pipeline information
     * @return a Response instance appropriate for the pipeline type
     */
    @SuppressWarnings("rawtypes")
    public static Response of(HttpServerExchange exchange) {
        var pi = Request.pipelineInfo(exchange);;

        if (pi.getType() == PipelineInfo.PIPELINE_TYPE.SERVICE) {
            return ServiceResponse.of(exchange);
        } else {
            return ByteArrayProxyResponse.of(exchange);
        }
    }

    /**
     * Retrieves the Content-Type header from the HTTP response.
     * <p>
     * This static utility method provides direct access to the response's
     * Content-Type header without requiring a Response instance.
     * </p>
     *
     * @param exchange the HTTP server exchange to extract the content type from
     * @return the Content-Type header value, or null if not present
     */
    public static String getContentType(HttpServerExchange exchange) {
        return exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    /**
     * Returns the Content-Type header of the response.
     * <p>
     * This method provides access to the current Content-Type header value
     * that will be sent with the HTTP response.
     * </p>
     *
     * @return the response Content-Type, or null if not set
     */
    @Override
    public String getContentType() {
        if (getHeaders().get(Headers.CONTENT_TYPE) != null) {
            return getHeaders().get(Headers.CONTENT_TYPE).getFirst();
        } else {
            return null;
        }
    }

    /**
     * Sets the Content-Type header for the response.
     * <p>
     * This method sets the MIME type that describes the format of the response content.
     * Common values include "application/json", "text/html", "text/plain", etc.
     * </p>
     *
     * @param responseContentType the Content-Type to set for the response
     */
    public void setContentType(String responseContentType) {
        setHeader(Headers.CONTENT_TYPE, responseContentType);
    }

    /**
     * Convenience method to set the Content-Type header to "application/json".
     * <p>
     * This is a commonly used method for JSON API responses, equivalent to
     * calling {@code setContentType("application/json")}.
     * </p>
     */
    public void setContentTypeAsJson() {
        setContentType(Exchange.JSON_MEDIA_TYPE);
    }

    /**
     * Returns the HTTP status code for this response.
     * <p>
     * The status code indicates the result of the HTTP request processing.
     * Common values include 200 (OK), 404 (Not Found), 500 (Internal Server Error), etc.
     * </p>
     *
     * @return the HTTP status code, or -1 if not explicitly set
     */
    public int getStatusCode() {
        var wrappedExchange = getWrappedExchange();

        if (wrappedExchange == null || wrappedExchange.getAttachment(STATUS_CODE) == null) {
            return -1;
        } else {
            return wrappedExchange.getAttachment(STATUS_CODE);
        }
    }

    /**
     * Sets the HTTP status code for this response.
     * <p>
     * The status code should reflect the outcome of the request processing.
     * This method stores the status code in the exchange attachment system
     * for later use by the response writing pipeline.
     * </p>
     *
     * @param responseStatusCode the HTTP status code to set
     */
    public void setStatusCode(int responseStatusCode) {
        getWrappedExchange().putAttachment(STATUS_CODE, responseStatusCode);
    }

    /**
     * Returns the HTTP headers for this response.
     * <p>
     * The returned HeaderMap allows for multiple values per header name
     * and provides case-insensitive header name matching. Headers added
     * to this map will be included in the HTTP response sent to the client.
     * </p>
     *
     * @return the mutable response headers map
     */
    public HeaderMap getHeaders() {
        return wrapped.getResponseHeaders();
    }

    /**
     * Returns the first value of the specified response header.
     * <p>
     * Note: An HTTP header can have multiple values. This method only returns
     * the first one. Use {@link #getHeaders()} to access all header values.
     * Header name matching is case-insensitive.
     * </p>
     *
     * @param name the name of the header to return
     * @return the first value of the response header, or null if the header is not present
     */
    public String getHeader(HttpString name) {
        return getHeaders().getFirst(name);
    }

    /**
     * Returns the first value of the specified response header.
     * <p>
     * Note: An HTTP header can have multiple values. This method only returns
     * the first one. Use {@link #getHeaders()} to access all header values.
     * Header name matching is case-insensitive.
     * </p>
     *
     * @param name the name of the header to return
     * @return the first value of the response header, or null if the header is not present
     */
    public String getHeader(String name) {
        return getHeaders().getFirst(HttpString.tryFromString(name));
    }

    /**
     * Sets a response header value, replacing any existing values for that header.
     * <p>
     * Note: An HTTP header can have multiple values. This method sets the given
     * value while clearing any existing ones. Use {@code getHeaders().add(name, value)}
     * to add a value without clearing existing ones.
     * </p>
     *
     * @param name the name of the header to set
     * @param value the value to set for the header
     */
    public void setHeader(HttpString name, String value) {
        if (getHeaders().get(name) == null) {
            getHeaders().put(name, value);
        } else {
            getHeaders().get(name).clear();
            getHeaders().get(name).add(value);
        }
    }

    /**
     * Sets a response header value, replacing any existing values for that header.
     * <p>
     * Note: An HTTP header can have multiple values. This method sets the given
     * value while clearing any existing ones. Use {@code getHeaders().add(name, value)}
     * to add a value without clearing existing ones.
     * </p>
     *
     * @param name the name of the header to set
     * @param value the value to set for the header
     */
    public void setHeader(String name, String value) {
        if (getHeaders().get(name) == null) {
            getHeaders().put(HttpString.tryFromString(name), value);
        } else {
            getHeaders().get(HttpString.tryFromString(name)).clear();
            getHeaders().get(HttpString.tryFromString(name)).add(value);
        }
    }

    /**
     * Returns the MDC (Mapped Diagnostic Context) for logging purposes.
     * <p>
     * The MDC context is bound to the thread during request processing. In case of
     * a thread switch (common in asynchronous processing), the context must be
     * restored using {@code MDC.setContextMap(response.getMDCContext())} to maintain
     * logging context across thread boundaries.
     * </p>
     * <p>
     * This is particularly important for maintaining request correlation IDs,
     * user information, and other contextual data in log messages throughout
     * the entire request processing pipeline.
     * </p>
     *
     * @return the MDC context map, or null if no context has been set
     */
    public Map<String, String> getMDCContext() {
        return getWrappedExchange().getAttachment(MDC_CONTEXT_KEY);
    }

    /**
     * Sets the MDC (Mapped Diagnostic Context) for logging purposes.
     * <p>
     * This method stores the logging context in the exchange attachment system
     * so it can be retrieved and restored when processing continues on different
     * threads. This is essential for maintaining consistent logging context
     * throughout asynchronous request processing.
     * </p>
     *
     * @param mdcCtx the MDC context map to store
     */
    public void setMDCContext(Map<String, String> mdcCtx) {
        getWrappedExchange().putAttachment(MDC_CONTEXT_KEY, mdcCtx);
    }

    /**
     * Sets the response in an error state with the specified status code, message, and optional throwable.
     * <p>
     * This abstract method must be implemented by subclasses to provide error response
     * formatting appropriate for their content type. The method should set the HTTP
     * status code and create a properly formatted error response body.
     * </p>
     * <p>
     * Implementations should handle the error information consistently and provide
     * meaningful error responses that clients can understand and process appropriately.
     * </p>
     *
     * @param code the HTTP status code to set (e.g., 400, 404, 500)
     * @param message the error message to include in the response
     * @param t an optional throwable that caused the error (can be null)
     */
    public abstract void setInError(int code, String message, Throwable t);

    /**
     * Sets the response in an error state with the specified status code and message.
     * <p>
     * This convenience method calls {@link #setInError(int, String, Throwable)} with
     * a null throwable parameter. It's useful when the error condition doesn't have
     * an associated exception.
     * </p>
     *
     * @param code the HTTP status code to set (e.g., 400, 404, 500)
     * @param message the error message to include in the response
     */
    public void setInError(int code, String message) {
        setInError(code, message, null);
    }
}
