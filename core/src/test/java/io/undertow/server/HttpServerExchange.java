/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package io.undertow.server;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.security.api.SecurityContext;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
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
            this.queryParameters = new TreeMap<>();
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

    private HeaderMap responseHeaders;

    /**
     * @return the response headers, created lazily on first access
     */
    public HeaderMap getResponseHeaders() {
        if (responseHeaders == null) {
            responseHeaders = new HeaderMap();
        }
        return responseHeaders;
    }

    private final RecordingSender responseSender = new RecordingSender();

    /**
     * @return a {@link Sender} that records the last content passed to
     *         {@code send(String)}, retrievable via {@link #getSentContent()}
     */
    public Sender getResponseSender() {
        return responseSender;
    }

    /**
     * @return the content last passed to {@code getResponseSender().send(String)},
     *         or {@code null} if nothing was sent
     */
    public String getSentContent() {
        return responseSender.sentContent;
    }

    /**
     * Minimal {@link Sender} fake that only records String content sent via
     * {@code send(String)}/{@code send(String, Charset)}; every other method is a no-op.
     */
    private static class RecordingSender implements Sender {
        private String sentContent;

        @Override
        public void send(ByteBuffer buffer, IoCallback callback) {
        }

        @Override
        public void send(ByteBuffer[] buffer, IoCallback callback) {
        }

        @Override
        public void send(ByteBuffer buffer) {
        }

        @Override
        public void send(ByteBuffer[] buffer) {
        }

        @Override
        public void send(String data, IoCallback callback) {
            this.sentContent = data;
        }

        @Override
        public void send(String data, Charset charset, IoCallback callback) {
            this.sentContent = data;
        }

        @Override
        public void send(String data) {
            this.sentContent = data;
        }

        @Override
        public void send(String data, Charset charset) {
            this.sentContent = data;
        }

        @Override
        public void transferFrom(FileChannel channel, IoCallback callback) {
        }

        @Override
        public void close(IoCallback callback) {
        }

        @Override
        public void close() {
        }
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
