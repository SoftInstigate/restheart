/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
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
public class QueryStringRebuilder extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(QueryStringRebuilder.class);

    static final AttachmentKey<String> ORIGINAL_QUERY_STRING
            = AttachmentKey.create(String.class);

    /**
     * Creates a new instance of QueryStringRebuiler
     *
     * @param next
     */
    public QueryStringRebuilder(PipelinedHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of QueryStringRebuiler
     *
     */
    public QueryStringRebuilder() {
        super(null);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // save the original request URI
        setOriginalQueryString(exchange);
        
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

    private void setOriginalQueryString(HttpServerExchange exchange) {
        if (exchange.getAttachment(ORIGINAL_QUERY_STRING) == null) {
            exchange.putAttachment(ORIGINAL_QUERY_STRING, 
                    exchange.getQueryString());
        }
    }

    public static String getOriginalQueryString(HttpServerExchange exchange) {
        var oqs = exchange.getAttachment(ORIGINAL_QUERY_STRING);

        return oqs == null ? exchange.getQueryString() : oqs;
    }
}
