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

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.PathTemplate;
import io.undertow.util.PathTemplateMatcher;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.restheart.exchange.ExchangeKeys.METHOD;

/**
 * The root class for implementing a Request providing the implementation for
 * common methods.
 * <p>
 * This abstract class wraps Undertow's HttpServerExchange to provide simplified
 * access to HTTP request elements such as headers, query parameters, path parameters,
 * cookies, and authentication information. It serves as the base class for all
 * specific request types in the RESTHeart framework.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> the generic type representing the request content
 */
public abstract class Request<T> extends Exchange<T> {

    /** String constant for forward slash character. */
    public static final String SLASH = "/";

    /** String constant for HTTP PATCH method. */
    public static final String PATCH = "PATCH";

    /** String constant for underscore character. */
    public static final String UNDERSCORE = "_";

    /** Attachment key for storing request-specific parameters. */
    private static final AttachmentKey<Map<String, Object>> ATTACHED_PARAMS_KEY = AttachmentKey.create(Map.class);

    /** Attachment key for storing pipeline information. */
    public static final AttachmentKey<PipelineInfo> PIPELINE_INFO_KEY = AttachmentKey.create(PipelineInfo.class);

    /** Attachment key for storing request start time. */
    private static final AttachmentKey<Long> START_TIME_KEY = AttachmentKey.create(Long.class);

    /** Attachment key for storing X-Forwarded headers. */
    private static final AttachmentKey<Map<String, List<String>>> XFORWARDED_HEADERS = AttachmentKey.create(Map.class);

    /** Attachment key for blocking authentication due to too many requests. */
    private static final AttachmentKey<Boolean> BLOCK_AUTH_FOR_TOO_MANY_REQUESTS = AttachmentKey.create(Boolean.class);

    /**
     * Constructs a Request wrapping the provided HttpServerExchange.
     * <p>
     * Initializes the attached parameters map if it doesn't already exist.
     * </p>
     *
     * @param exchange the HttpServerExchange to wrap
     */
    protected Request(HttpServerExchange exchange) {
        super(exchange);
        // init attached params
        if (exchange.getAttachment(ATTACHED_PARAMS_KEY) == null) {
            exchange.putAttachment(ATTACHED_PARAMS_KEY, new HashMap<>());
        }
    }

    /**
     * Factory method to create an appropriate Request instance based on the pipeline type.
     * <p>
     * Creates either a ServiceRequest for service pipelines or a ByteArrayProxyRequest
     * for other pipeline types.
     * </p>
     *
     * @param exchange the HttpServerExchange to create a Request for
     * @return a Request instance appropriate for the pipeline type
     */
    @SuppressWarnings("rawtypes")
    public static Request of(HttpServerExchange exchange) {
        var pi = pipelineInfo(exchange);

        if (pi.getType() == PipelineInfo.PIPELINE_TYPE.SERVICE) {
            return ServiceRequest.of(exchange);
        } else {
            return ByteArrayProxyRequest.of(exchange);
        }
    }

    /**
     * Retrieves the Content-Type header from the HTTP request.
     *
     * @param exchange the HttpServerExchange to extract the content type from
     * @return the Content-Type header value, or null if not present
     */
    public static String getContentType(HttpServerExchange exchange) {
        return exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    /**
     * Returns the request path portion of the URL.
     * <p>
     * For example, if the full URL is "http://example.com/api/users?id=123",
     * this method returns "/api/users".
     * </p>
     *
     * @return the request path
     */
    public String getPath() {
        return wrapped.getRequestPath();
    }

    /**
     * Returns the complete request URL including protocol, host, port, and path.
     * <p>
     * For example: "http://example.com:8080/api/users"
     * </p>
     *
     * @return the complete request URL
     */
    public String getURL() {
        return wrapped.getRequestURL();
    }

    /**
     * Returns the query string portion of the URL.
     * <p>
     * For example, if the URL is "http://example.com/api/users?id=123&name=john",
     * this method returns "id=123&name=john".
     * </p>
     *
     * @return the query string, or null if no query string is present
     */
    public String getQueryString() {
        return wrapped.getQueryString();
    }

    /**
     * Returns the HTTP method of the request as a METHOD enum value.
     * <p>
     * Supports standard HTTP methods: GET, POST, PUT, DELETE, PATCH, OPTIONS.
     * Unknown methods are returned as METHOD.OTHER.
     * </p>
     *
     * @return the HTTP request method
     */
    public METHOD getMethod() {
        return selectMethod(getWrappedExchange().getRequestMethod());
    }

    /**
     * Returns the content length of the request body.
     * <p>
     * This is determined from the Content-Length header if present.
     * </p>
     *
     * @return the content length in bytes, or -1 if unknown
     */
    public long getRequestContentLength() {
        return wrapped.getRequestContentLength();
    }

    /**
     * Returns a mutable map of query parameters from the request URL.
     * <p>
     * Each parameter name maps to a Deque of values, allowing for multiple
     * values with the same parameter name. For example, "?id=1&id=2&name=john"
     * would result in {"id": ["1", "2"], "name": ["john"]}.
     * </p>
     *
     * @return a mutable map of query parameters
     */
    public Map<String, Deque<String>> getQueryParameters() {
        return wrapped.getQueryParameters();
    }

    /**
     * @param name the name of the query parameter
     * @param defaultValue the default value of the query parameter to be used if request does not specifies it
     * @return the value of the query parameter or defaultValue if not present
     * @deprecated use {@link org.restheart.exchange.Request#getQueryParameterOrDefault} instead
     * This method contains a typo in the name and will be removed in a future release
     */
    @Deprecated
    public String getQueryParameterOfDefault(String name, String defaultValue) {
        return wrapped.getQueryParameters().containsKey(name)
            ?  wrapped.getQueryParameters().get(name).getFirst()
            : defaultValue;
    }

    /**
     * Returns the first value of a query parameter, or a default value if not present.
     * <p>
     * If the parameter has multiple values, only the first one is returned.
     * Use {@link #getQueryParameters()} to access all values.
     * </p>
     *
     * @param name the name of the query parameter
     * @param defaultValue the default value to return if the query parameter is not present
     * @return the first value of the query parameter or defaultValue if not present
     */
    public String getQueryParameterOrDefault(String name, String defaultValue) {
        return wrapped.getQueryParameters().containsKey(name)
                ? wrapped.getQueryParameters().get(name).getFirst()
                : defaultValue;
    }

    /**
     * Returns the HTTP headers of the request.
     * <p>
     * The returned HeaderMap allows for multiple values per header name
     * and provides case-insensitive header name matching.
     * </p>
     *
     * @return the request headers
     */
    public HeaderMap getHeaders() {
        return wrapped.getRequestHeaders();
    }

    /**
     * Returns the first value of the specified header.
     * <p>
     * Note: An HTTP header can have multiple values. This method only returns
     * the first one. Use {@link #getHeaders()} to access all header values.
     * Header name matching is case-insensitive.
     * </p>
     *
     * @param name the name of the header to return
     * @return the first value of the header, or null if the header is not present
     */
    public String getHeader(String name) {
        return getHeaders().getFirst(HttpString.tryFromString(name));
    }

    /**
     * Sets a header value, replacing any existing values for that header.
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
     * Sets a header value, replacing any existing values for that header.
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
     * Retrieves a cookie from the request by name.
     *
     * @param name the name of the cookie to return
     * @return the cookie with the specified name, or null if not found
     */
    public Cookie getCookie(String name) {
        return wrapped.getRequestCookie(name);
    }

    /**
     * Extracts path parameters from the request path using a path template.
     * <p>
     * Example: If pathTemplate is "/users/{id}/posts/{postId}" and the request
     * path is "/users/123/posts/456", this method returns a map containing
     * {"id": "123", "postId": "456"}.
     * </p>
     *
     * @param pathTemplate the path template with parameter placeholders in curly braces
     * @return a map of parameter names to values, or an empty map if no match
     * @throws IllegalArgumentException if the path template is malformed
     */
    public Map<String, String> getPathParams(String pathTemplate) {
        var ptm = new PathTemplateMatcher<String>();

        try {
            ptm.add(PathTemplate.create(pathTemplate), "");
        } catch (Throwable t) {
            throw new IllegalArgumentException("wrong path template", t);
        }

        var match = ptm.match(getPath());

        return match != null ? ptm.match(getPath()).getParameters() : new HashMap<>();
    }

    /**
     * Extracts a specific path parameter from the request path using a path template.
     * <p>
     * Example: If pathTemplate is "/users/{id}", paramName is "id", and the
     * request path is "/users/123", this method returns "123".
     * </p>
     *
     * @param pathTemplate the path template with parameter placeholders in curly braces
     * @param paramName the name of the parameter to extract
     * @return the value of the specified path parameter, or null if not found
     * @throws IllegalArgumentException if the path template is malformed
     */
    public String getPathParam(String pathTemplate, String paramName) {
        var params = getPathParams(pathTemplate);

        return params != null ? params.get(paramName) : null;
    }

    /**
     * Returns the Content-Type header of the request.
     *
     * @return the request Content-Type, or null if not specified
     */
    @Override
    public String getContentType() {
        return getContentType(getWrappedExchange());
    }

    /**
     * Sets the Content-Type header for the request.
     *
     * @param responseContentType the Content-Type to set
     */
    public void setContentType(String responseContentType) {
        getHeaders().put(Headers.CONTENT_TYPE, responseContentType);
    }

    /**
     * Convenience method to set the Content-Type header to "application/json".
     */
    public void setContentTypeAsJson() {
        setContentType("application/json");
    }

    /**
     * Sets the Content-Length header for the request.
     *
     * @param length the content length in bytes
     */
    protected void setContentLength(int length) {
        getHeaders().put(Headers.CONTENT_LENGTH, length);
    }

    /**
     * Returns the timestamp when request processing started.
     *
     * @return the request start time in milliseconds since epoch, or null if not set
     */
    public Long getStartTime() {
        return getWrappedExchange().getAttachment(START_TIME_KEY);
    }

    /**
     * Sets the timestamp when request processing started.
     *
     * @param requestStartTime the request start time in milliseconds since epoch
     */
    public void setStartTime(Long requestStartTime) {
        getWrappedExchange().putAttachment(START_TIME_KEY, requestStartTime);
    }

    /**
     * Returns the authenticated account associated with this request.
     * <p>
     * This method checks the security context to retrieve the authenticated account.
     * Returns null if no authentication has occurred or if authentication failed.
     * </p>
     *
     * @return the authenticated account, or null if not authenticated
     */
    public Account getAuthenticatedAccount() {
        return getWrappedExchange().getSecurityContext() != null
                ? getWrappedExchange().getSecurityContext().getAuthenticatedAccount()
                : null;
    }

    /**
     * Checks if the request is from an authenticated user.
     *
     * @return true if the request has an authenticated account, false otherwise
     */
    @Override
    public boolean isAuthenticated() {
        return getAuthenticatedAccount() != null;
    }

    /**
     * Adds an X-Forwarded header to be included in proxied requests.
     * <p>
     * X-Forwarded headers are used to pass information about the original request
     * to backend services when proxying. Common examples include X-Forwarded-For,
     * X-Forwarded-Host, and X-Forwarded-Proto.
     * </p>
     *
     * @param key the header suffix (e.g., "For" for X-Forwarded-For)
     * @param value the header value to add
     */
    public void addXForwardedHeader(String key, String value) {
        if (wrapped.getAttachment(XFORWARDED_HEADERS) == null) {
            wrapped.putAttachment(XFORWARDED_HEADERS, new LinkedHashMap<>());

        }

        var values = wrapped.getAttachment(XFORWARDED_HEADERS).get(key);

        if (values == null) {
            values = new ArrayList<>();
            wrapped.getAttachment(XFORWARDED_HEADERS).put(key, values);
        }

        values.add(value);
    }

    /**
     * Returns all X-Forwarded headers that have been added to this request.
     *
     * @return a map of X-Forwarded header keys to their values, or null if none exist
     */
    public Map<String, List<String>> getXForwardedHeaders() {
        return getWrappedExchange().getAttachment(XFORWARDED_HEADERS);
    }

    /**
     * Retrieves the pipeline information for a given exchange.
     * <p>
     * Pipeline information identifies which type of pipeline (service, proxy,
     * or static resource) is handling the request.
     * </p>
     *
     * @param exchange the HttpServerExchange to get pipeline info for
     * @return the PipelineInfo, or null if not set
     */
    public static PipelineInfo pipelineInfo(HttpServerExchange exchange) {
        return exchange.getAttachment(PIPELINE_INFO_KEY);
    }

    /**
     * Sets the pipeline information for a given exchange.
     *
     * @param exchange the exchange to bind the pipeline info to
     * @param pipelineInfo the pipeline information to set
     */
    public static void setPipelineInfo(HttpServerExchange exchange, PipelineInfo pipelineInfo) {
        exchange.putAttachment(PIPELINE_INFO_KEY, pipelineInfo);
    }

    /**
     * Returns the pipeline information for this request.
     * <p>
     * Pipeline information identifies which type of pipeline (service, proxy,
     * or static resource) is handling the request.
     * </p>
     *
     * @return the PipelineInfo, or null if not set
     */
    public PipelineInfo getPipelineInfo() {
        return getWrappedExchange().getAttachment(PIPELINE_INFO_KEY);
    }

    /**
     * Retrieves the pipeline information for a given exchange.
     * <p>
     * Pipeline information identifies which type of pipeline (service, proxy,
     * or static resource) is handling the request.
     * </p>
     *
     * @param exchange the HttpServerExchange to get pipeline info for
     * @return the PipelineInfo, or null if not set
     */
    public static PipelineInfo getPipelineInfo(HttpServerExchange exchange) {
        return exchange.getAttachment(PIPELINE_INFO_KEY);
    }

    /**
     * Sets the pipeline information for this request.
     *
     * @param pipelineInfo the pipeline information to set
     */
    public void setPipelineInfo(PipelineInfo pipelineInfo) {
        getWrappedExchange().putAttachment(PIPELINE_INFO_KEY, pipelineInfo);
    }

    /**
     * Checks if the request method is DELETE.
     *
     * @return true if the request method is DELETE, false otherwise
     */
    public boolean isDelete() {
        return getMethod() == METHOD.DELETE;
    }

    /**
     * Checks if the request method is GET.
     *
     * @return true if the request method is GET, false otherwise
     */
    public boolean isGet() {
        return getMethod() == METHOD.GET;
    }

    /**
     * Checks if the request method is OPTIONS.
     *
     * @return true if the request method is OPTIONS, false otherwise
     */
    public boolean isOptions() {
        return getMethod() == METHOD.OPTIONS;
    }

    /**
     * Checks if the request method is PATCH.
     *
     * @return true if the request method is PATCH, false otherwise
     */
    public boolean isPatch() {
        return getMethod() == METHOD.PATCH;
    }

    /**
     * Checks if the request method is POST.
     *
     * @return true if the request method is POST, false otherwise
     */
    public boolean isPost() {
        return getMethod() == METHOD.POST;
    }

    /**
     * Checks if the request method is PUT.
     *
     * @return true if the request method is PUT, false otherwise
     */
    public boolean isPut() {
        return getMethod() == METHOD.PUT;
    }

    /**
     * Converts an Undertow HttpString method to a RESTHeart METHOD enum.
     * <p>
     * Maps standard HTTP methods to their corresponding METHOD enum values.
     * Unknown methods are mapped to METHOD.OTHER.
     * </p>
     *
     * @param _method the HttpString representation of the HTTP method
     * @return the corresponding METHOD enum value
     */
    private static METHOD selectMethod(HttpString _method) {
        if (Methods.GET.equals(_method)) {
            return METHOD.GET;
        } else if (Methods.POST.equals(_method)) {
            return METHOD.POST;
        } else if (Methods.PUT.equals(_method)) {
            return METHOD.PUT;
        } else if (Methods.DELETE.equals(_method)) {
            return METHOD.DELETE;
        } else if (PATCH.equals(_method.toString())) {
            return METHOD.PATCH;
        } else if (Methods.OPTIONS.equals(_method)) {
            return METHOD.OPTIONS;
        } else {
            return METHOD.OTHER;
        }
    }

    /**
     * Marks this request to be blocked due to too many requests.
     * <p>
     * If called BEFORE authentication, the request will be aborted
     * with a 429 Too Many Requests response. This is typically used
     * by rate limiting mechanisms to prevent abuse.
     * </p>
     */
    public void blockForTooManyRequests() {
        getWrappedExchange().putAttachment(BLOCK_AUTH_FOR_TOO_MANY_REQUESTS, true);
    }

    /**
     * Checks if this request has been marked for blocking due to too many requests.
     * <p>
     * This method is used by authentication mechanisms to determine if a request
     * should be rejected before processing.
     * </p>
     *
     * @return true if the request is blocked for too many requests, false otherwise
     */
    public boolean isBlockForTooManyRequests() {
        var block = getWrappedExchange().getAttachment(BLOCK_AUTH_FOR_TOO_MANY_REQUESTS);

        return block == null ? false : block;
    }

    /**
     * Retrieves the key-value entries attached to the current request.
     * <p>
     * This method always returns a non-null map. If no parameters were previously attached,
     * an empty map is returned.
     *
     * @return a non-null map containing the attached parameters.
     */
    public Map<String, Object> attachedParams() {
        return getWrappedExchange().getAttachment(ATTACHED_PARAMS_KEY);
    }

    /**
     * Retrieves the value of a specific attached parameter from the request, cast to the expected type.
     *
     * @param <T> the expected type of the parameter value.
     * @param key the key of the parameter to retrieve; must not be {@code null}.
     * @return the value associated with the given key, cast to type {@code T}, or {@code null} if the key is not present.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws ClassCastException if the value cannot be cast to type {@code T}.
     */
    @SuppressWarnings("unchecked")
    public <T> T attachedParam(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        var val = getWrappedExchange().getAttachment(ATTACHED_PARAMS_KEY).get(key);

        return val == null ? null : (T) attachedParams().get(key);
    }

    /**
     * Adds a key-value entry to the attached parameters of the current request.
     * <p>
     * If no parameters exist, this method ensures that a new map is created before adding the entry.
     *
     * @param key   the key of the parameter to attach; must not be {@code null}.
     * @param value the value of the parameter to attach; can be {@code null}.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    public void attachParam(String key, Object value) {
        Objects.requireNonNull(key, "Key must not be null");

        Map<String, Object> attachedParams = getWrappedExchange().getAttachment(ATTACHED_PARAMS_KEY);

        // Ensure the map is initialized if not already present
        if (attachedParams == null) {
            throw new IllegalStateException("Attached parameters map is unexpectedly null");
        }

        attachedParams.put(key, value);
    }
}
