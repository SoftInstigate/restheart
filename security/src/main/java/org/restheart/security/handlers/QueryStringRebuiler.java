/*
 * RESTHeart Security
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
package org.restheart.security.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.QueryParameterUtils;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * rebuild the query string from the exchange.getQueryParameters() that might
 * have been updated by request interceptors. it also encodes values
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 */
public class QueryStringRebuiler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(QueryStringRebuiler.class);

    /**
     * Creates a new instance of QueryStringRebuiler
     *
     * @param next
     */
    public QueryStringRebuiler(PipedHttpHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of QueryStringRebuiler
     *
     * @param next
     */
    public QueryStringRebuiler(HttpHandler next) {
        super(null);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Map<String, Deque<String>> qps = exchange.getQueryParameters();

        var decodedQueryParameters = new TreeMap<String, Deque<String>>();

        final var encoding = QueryParameterUtils.getQueryParamEncoding(exchange);

        for (var k : qps.keySet()) {
            var values = qps.get(k);

            var nvalues = new ArrayDeque<String>(values.size());

            for (var value : values) {
                nvalues.add(URLEncoder.encode(value, encoding));
            }

            decodedQueryParameters.put(k, nvalues);
        }

        var newqs = QueryParameterUtils.buildQueryString(decodedQueryParameters);

        exchange.setQueryString(newqs);

        next(exchange);
    }
}
