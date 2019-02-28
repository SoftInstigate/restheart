/*
 * uIAM - the IAM for microservices
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.server;

import io.undertow.security.api.SecurityContext;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

/**
 * A mock for io.undertow.server.HttpServerExchange The original class is final
 * so it can't be mocked directly. Then use Mockito:
 * <code>HttpServerExchange ex = mock(HttpServerExchange.class);</code>
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public class HttpServerExchange extends AbstractAttachable {

    private int statusCode = 0;
    private String queryString;
    private String requestPath;
    private String relativePath;
    private HttpString requestMethod;
    private Map<String, Deque<String>> queryParameters;

    public HttpServerExchange() {
    }

    public HttpServerExchange endExchange() {
        return this;
    }

    /**
     * Returns a mutable map of query parameters.
     *
     * @return The query parameters
     */
    public Map<String, Deque<String>> getQueryParameters() {
        if (queryParameters == null) {
            queryParameters = new TreeMap<>();
        }
        return Collections.unmodifiableMap(queryParameters);
    }

    public HttpServerExchange addQueryParam(final String name, final String param) {
        if (queryParameters == null) {
            queryParameters = new TreeMap<>();
        }
        Deque<String> list = queryParameters.get(name);
        if (list == null) {
            queryParameters.put(name, list = new ArrayDeque<>(2));
        }
        list.add(param);
        return this;
    }

    void addExchangeCompleteListener(ExchangeCompletionListener listener) {
    }

    /**
     * @return the statusCode
     */
    public int getResponseCode() {
        return statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * @param statusCode the statusCode to set
     * @return
     */
    public HttpServerExchange setStatusCode(final int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    /**
     * @return the queryString
     */
    public String getQueryString() {
        return queryString;
    }

    /**
     * @param queryString the queryString to set
     * @return
     */
    public HttpServerExchange setQueryString(final String queryString) {
        this.queryString = queryString;
        return this;
    }

    /**
     * @return the requestPath
     */
    public String getRequestPath() {
        return requestPath;
    }

    /**
     * @param requestPath the requestPath to set
     */
    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    /**
     * @return the requestMethod
     */
    public HttpString getRequestMethod() {
        return requestMethod;
    }

    /**
     * @param requestMethod the requestMethod to set
     */
    public void setRequestMethod(HttpString requestMethod) {
        this.requestMethod = requestMethod;
    }

    public InputStream getInputStream() {
        return new ByteArrayInputStream("FAKE_STREAM".getBytes());
    }

    public HeaderMap getRequestHeaders() {
        return null;
    }

    public SecurityContext getSecurityContext() {
        return null;
    }

    /**
     * @return the relativePath
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * @param relativePath the relativePath to set
     */
    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }
}
