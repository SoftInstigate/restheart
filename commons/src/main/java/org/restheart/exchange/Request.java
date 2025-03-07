/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
 *
 * The root class for implementing a Request providing the implementation for
 * common methods
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> generic type
 */
public abstract class Request<T> extends Exchange<T> {

    // other constants
    public static final String SLASH = "/";
    public static final String PATCH = "PATCH";
    public static final String UNDERSCORE = "_";

    private static final AttachmentKey<Map<String, Object>> ATTACHED_PARAMS_KEY = AttachmentKey.create(Map.class);

    public static final AttachmentKey<PipelineInfo> PIPELINE_INFO_KEY = AttachmentKey.create(PipelineInfo.class);

    private static final AttachmentKey<Long> START_TIME_KEY = AttachmentKey.create(Long.class);

    private static final AttachmentKey<Map<String, List<String>>> XFORWARDED_HEADERS = AttachmentKey.create(Map.class);

    private static final AttachmentKey<Boolean> BLOCK_AUTH_FOR_TOO_MANY_REQUESTS = AttachmentKey.create(Boolean.class);

    protected Request(HttpServerExchange exchange) {
        super(exchange);
        // init attached params
        if (exchange.getAttachment(ATTACHED_PARAMS_KEY) == null) {
            exchange.putAttachment(ATTACHED_PARAMS_KEY, new HashMap<>());
        }
    }

    @SuppressWarnings("rawtypes")
    public static Request of(HttpServerExchange exchange) {
        var pi = pipelineInfo(exchange);

        if (pi.getType() == PipelineInfo.PIPELINE_TYPE.SERVICE) {
            return ServiceRequest.of(exchange);
        } else {
            return ByteArrayProxyRequest.of(exchange);
        }
    }

    public static String getContentType(HttpServerExchange exchange) {
        return exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    /**
     *
     * @return the request path
     */
    public String getPath() {
        return wrapped.getRequestPath();
    }

    /**
     *
     * @return the request URL
     */
    public String getURL() {
        return wrapped.getRequestURL();
    }

    /**
     *
     * @return the query string
     */
    public String getQueryString() {
        return wrapped.getQueryString();
    }

    /**
     *
     * @return the request method
     */
    public METHOD getMethod() {
        return selectMethod(getWrappedExchange().getRequestMethod());
    }

    /**
     *
     * @return a content lenght
     */
    public long getRequestContentLength() {
        return wrapped.getRequestContentLength();
    }

    /**
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
     * @param name the name of the query parameter
     * @param defaultValue the default value to return if the query parameter is not present
     * @return the value of the query parameter or defaultValue if not present
     */
    public String getQueryParameterOrDefault(String name, String defaultValue) {
        return wrapped.getQueryParameters().containsKey(name)
                ? wrapped.getQueryParameters().get(name).getFirst()
                : defaultValue;
    }

    /**
     *
     * @return the request headers
     */
    public HeaderMap getHeaders() {
        return wrapped.getRequestHeaders();
    }

    /**
     * note: an header can have multiple values. This only returns the first one.
     * use getHeaders() to get all the header's values
     *
     * @param name the name of the header to return
     * @return the first value of the header
     */
    public String getHeader(String name) {
        return getHeaders().getFirst(HttpString.tryFromString(name));
    }

    /**
     * note: an header can have multiple values. This sets the given value clearing
     * existing ones. use getHeaders().add(value) to add the value without clearing.
     *
     * @param name the name of the header to return
     * @param value
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
     * note: an header can have multiple values. This sets the given value clearing
     * existing ones. use getHeaders().add(value) to add the value without clearing.
     *
     * @param name the name of the header to return
     * @param value
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
     * @param name the name of the cookie to return
     * @return a the cookie
     */
    public Cookie getCookie(String name) {
        return wrapped.getRequestCookie(name);
    }

    /**
     * get path parameters using a template
     *
     * {@literal pathTemplate=/foo/{id} and URI=/foo/bar => returns a map with id=bar }
     *
     * @param pathTemplate the path template
     * @return the path parameters
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
     * get a path parameter using a path template
     *
     * eg {@literal pathTemplate=/foo/{id}, paramName=id and URI=/foo/bar => returns bar }
     *
     * @param pathTemplate the path template
     * @param paramName name of parameter
     * @return the path parameter
     */
    public String getPathParam(String pathTemplate, String paramName) {
        var params = getPathParams(pathTemplate);

        return params != null ? params.get(paramName) : null;
    }

    /**
     * @return the request ContentType
     */
    @Override
    public String getContentType() {
        return getContentType(getWrappedExchange());
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setContentType(String responseContentType) {
        getHeaders().put(Headers.CONTENT_TYPE, responseContentType);
    }

    /**
     * sets Content-Type=application/json
     */
    public void setContentTypeAsJson() {
        setContentType("application/json");
    }

    protected void setContentLength(int length) {
        getHeaders().put(Headers.CONTENT_LENGTH, length);
    }

    /**
     * @return the requestStartTime
     */
    public Long getStartTime() {
        return getWrappedExchange().getAttachment(START_TIME_KEY);
    }

    /**
     * @param requestStartTime the requestStartTime to set
     */
    public void setStartTime(Long requestStartTime) {
        getWrappedExchange().putAttachment(START_TIME_KEY, requestStartTime);
    }

    /**
     * @return the authenticatedAccount
     */
    public Account getAuthenticatedAccount() {
        return getWrappedExchange().getSecurityContext() != null
                ? getWrappedExchange().getSecurityContext().getAuthenticatedAccount()
                : null;
    }

    /**
     * @return true if account is authenticated
     */
    @Override
    public boolean isAuthenticated() {
        return getAuthenticatedAccount() != null;
    }

    /**
     * Add the header X-Forwarded-[key] to the proxied request; use it to pass to
     * the backend information otherwise lost proxying the request.
     *
     * @param key
     * @param value
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

    public Map<String, List<String>> getXForwardedHeaders() {
        return getWrappedExchange().getAttachment(XFORWARDED_HEADERS);
    }

    /**
     *
     * @param exchange
     * @return the PipelineInfo that allows to know which pipeline (service, proxy
     *         or static resource) is handling the exchange
     */
    public static PipelineInfo pipelineInfo(HttpServerExchange exchange) {
        return exchange.getAttachment(PIPELINE_INFO_KEY);
    }

    /**
     * @param exchange the exchange to bind the pipelineInfo to
     * @param pipelineInfo the pipelineInfo to set
     */
    public static void setPipelineInfo(HttpServerExchange exchange, PipelineInfo pipelineInfo) {
        exchange.putAttachment(PIPELINE_INFO_KEY, pipelineInfo);
    }

    /**
     *
     * @return the PipelineInfo that allows to know which pipeline (service, proxy
     *         or static resource) is handling the exchange
     */
    public PipelineInfo getPipelineInfo() {
        return getWrappedExchange().getAttachment(PIPELINE_INFO_KEY);
    }

    /**
     *
     * @param exchange
     * @return the PipelineInfo that allows to know which pipeline (service, proxy
     *         or static resource) is handling the exchange
     */
    public static PipelineInfo getPipelineInfo(HttpServerExchange exchange) {
        return exchange.getAttachment(PIPELINE_INFO_KEY);
    }

    /**
     * @param pipelineInfo the pipelineInfo to set
     */
    public void setPipelineInfo(PipelineInfo pipelineInfo) {
        getWrappedExchange().putAttachment(PIPELINE_INFO_KEY, pipelineInfo);
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.DELETE
     */
    public boolean isDelete() {
        return getMethod() == METHOD.DELETE;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.GET
     */
    public boolean isGet() {
        return getMethod() == METHOD.GET;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.OPTIONS
     */
    public boolean isOptions() {
        return getMethod() == METHOD.OPTIONS;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PATCH
     */
    public boolean isPatch() {
        return getMethod() == METHOD.PATCH;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.POST
     */
    public boolean isPost() {
        return getMethod() == METHOD.POST;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PUT
     */
    public boolean isPut() {
        return getMethod() == METHOD.PUT;
    }

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
     * If called BEFORE authentication, the request will be aborted
     * with a 429 Too Many Requests response.
     */
    public void blockForTooManyRequests() {
        getWrappedExchange().putAttachment(BLOCK_AUTH_FOR_TOO_MANY_REQUESTS, true);
    }

    /**
     * @return true if the request is blocked for too many requests
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
