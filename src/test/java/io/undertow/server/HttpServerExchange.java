/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.undertow.server;

import io.undertow.util.AbstractAttachable;
import io.undertow.util.HttpString;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
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

    private int responseCode = 0;
    private String queryString;
    private String requestPath;
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
            queryParameters = new TreeMap<String, Deque<String>>();
        }
        return queryParameters;
    }

    public HttpServerExchange addQueryParam(final String name, final String param) {
        if (queryParameters == null) {
            queryParameters = new TreeMap<String, Deque<String>>();
        }
        Deque<String> list = queryParameters.get(name);
        if (list == null) {
            queryParameters.put(name, list = new ArrayDeque<String>(2));
        }
        list.add(param);
        return this;
    }

    /**
     * @return the responseCode
     */
    public int getResponseCode() {
        return responseCode;
    }

    /**
     * @param responseCode the responseCode to set
     * @return
     */
    public HttpServerExchange setResponseCode(final int responseCode) {
        this.responseCode = responseCode;
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

}
