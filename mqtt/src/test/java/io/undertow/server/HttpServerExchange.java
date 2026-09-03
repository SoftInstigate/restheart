/*-
 * ========================LICENSE_START=================================
 * restheart-mqtt
 * %%
 * Copyright (C) 2026 SoftInstigate
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

import org.xnio.channels.StreamSourceChannel;

import io.undertow.security.api.SecurityContext;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

/**
 * A test double for {@code io.undertow.server.HttpServerExchange}. The real class is
 * {@code final} so it cannot be mocked or subclassed; this stand-in lives in the same
 * package/name so that classes compiled against it in {@code src/test/java} shadow the
 * real dependency jar on the test classpath. This mirrors the convention already used
 * in the {@code commons}, {@code mongodb} and {@code security} modules.
 * <p>
 * It supports enough of the real API to construct and exercise {@link
 * org.restheart.exchange.JsonRequest} and {@link org.restheart.exchange.JsonResponse}
 * instances in unit tests: request method, query parameters, request/response headers,
 * and attachments (inherited from the real {@link AbstractAttachable}, which backs
 * status code storage and pipeline-info lookups).
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class HttpServerExchange extends AbstractAttachable {

    private String queryString;
    private String requestPath;
    private String relativePath;
    private HttpString requestMethod;
    private Map<String, Deque<String>> queryParameters;
    private final HeaderMap requestHeaders = new HeaderMap();
    private final HeaderMap responseHeaders = new HeaderMap();

    public HttpServerExchange() {
    }

    public HttpServerExchange(ServerConnection conn) {
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
     * @return the queryString
     */
    public String getQueryString() {
        return queryString;
    }

    /**
     * @param queryString the queryString to set
     * @return this exchange
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
     * @return this exchange
     */
    public HttpServerExchange setRequestMethod(HttpString requestMethod) {
        this.requestMethod = requestMethod;
        return this;
    }

    public InputStream getInputStream() {
        return new ByteArrayInputStream("FAKE_STREAM".getBytes());
    }

    public HeaderMap getRequestHeaders() {
        return requestHeaders;
    }

    public HeaderMap getResponseHeaders() {
        return responseHeaders;
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

    public StreamSourceChannel getRequestChannel() {
        return null;
    }

    public long getRequestContentLength() {
        return 0;
    }

    public String getHostName() {
        return "localhost";
    }
}
