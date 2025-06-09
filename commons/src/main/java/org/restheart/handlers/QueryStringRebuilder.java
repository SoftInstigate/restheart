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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.QueryParameterUtils;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

/**
 * A handler that rebuilds and encodes the query string from potentially modified query parameters.
 * 
 * <p>This handler serves a critical role in the request processing pipeline by:</p>
 * <ul>
 *   <li>Capturing the original query string before any modifications</li>
 *   <li>Rebuilding the query string after request interceptors may have modified parameters</li>
 *   <li>Properly URL-encoding all parameter values to ensure valid URLs</li>
 *   <li>Preserving the original query string for later reference</li>
 * </ul>
 * 
 * <p>The handler is particularly useful when request interceptors need to modify query
 * parameters, as Undertow's {@code HttpServerExchange.getQueryParameters()} returns
 * a mutable map that can be modified but doesn't automatically update the query string.</p>
 * 
 * <p>Example usage in a pipeline:</p>
 * <pre>{@code
 * PipelinedHandler pipeline = PipelinedHandler.pipe(
 *     new QueryStringRebuilder(),
 *     new ParameterModifyingInterceptor(),
 *     new BusinessLogicHandler()
 * );
 * }</pre>
 * 
 * <p>After this handler processes a request:</p>
 * <ul>
 *   <li>The original query string is stored as an attachment on the exchange</li>
 *   <li>The query string is rebuilt from the current parameter map with proper encoding</li>
 *   <li>Subsequent handlers see the updated, properly encoded query string</li>
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see #getOriginalQueryString(HttpServerExchange)
 */
public class QueryStringRebuilder extends PipelinedHandler {

    /**
     * Attachment key for storing the original query string on the exchange.
     * This allows handlers to access the query string as it was before any modifications.
     */
    static final AttachmentKey<String> ORIGINAL_QUERY_STRING = AttachmentKey.create(String.class);

    /**
     * Creates a new instance of QueryStringRebuilder with a specified next handler.
     * 
     * <p>The handler will process the request by rebuilding the query string
     * and then forward the request to the next handler in the pipeline.</p>
     *
     * @param next the next handler in the pipeline to execute after rebuilding the query string
     */
    public QueryStringRebuilder(PipelinedHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of QueryStringRebuilder without a next handler.
     * 
     * <p>This constructor creates a terminal handler that rebuilds the query
     * string but doesn't forward the request to any subsequent handler.</p>
     */
    public QueryStringRebuilder() {
        super(null);
    }

    /**
     * Handles the request by rebuilding and encoding the query string.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Saves the original query string as an exchange attachment (on first invocation)</li>
     *   <li>Retrieves the current query parameters from the exchange</li>
     *   <li>URL-encodes each parameter value using the exchange's character encoding</li>
     *   <li>Rebuilds the query string with the encoded parameters</li>
     *   <li>Updates the exchange with the new query string</li>
     *   <li>Forwards the request to the next handler</li>
     * </ol>
     * 
     * <p>The encoding used for URL-encoding is determined by Undertow's
     * {@link QueryParameterUtils#getQueryParamEncoding(HttpServerExchange)} method,
     * which typically uses UTF-8 or the encoding specified in the request.</p>
     * 
     * <p>Parameter ordering is preserved using a {@link TreeMap}, ensuring
     * consistent query string generation.</p>
     *
     * @param exchange the HTTP server exchange containing the request to process
     * @throws Exception if an error occurs during processing or in the next handler
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

    /**
     * Stores the original query string as an exchange attachment.
     * 
     * <p>This method captures the query string before any modifications
     * are made by this or subsequent handlers. It only stores the value
     * on the first invocation to ensure the truly original value is preserved
     * even if this handler is invoked multiple times.</p>
     * 
     * @param exchange the exchange on which to store the original query string
     */
    private void setOriginalQueryString(HttpServerExchange exchange) {
        if (exchange.getAttachment(ORIGINAL_QUERY_STRING) == null) {
            exchange.putAttachment(ORIGINAL_QUERY_STRING,
                    exchange.getQueryString());
        }
    }

    /**
     * Retrieves the original query string from before any modifications were made.
     * 
     * <p>This static utility method allows any handler in the pipeline to access
     * the query string as it was when the request first entered the system,
     * regardless of any modifications made by interceptors or other handlers.</p>
     * 
     * <p>If the QueryStringRebuilder hasn't processed the exchange yet,
     * this method returns the current query string from the exchange.</p>
     * 
     * @param exchange the exchange from which to retrieve the original query string
     * @return the original query string if available, otherwise the current query string
     */
    public static String getOriginalQueryString(HttpServerExchange exchange) {
        var oqs = exchange.getAttachment(ORIGINAL_QUERY_STRING);

        return oqs == null ? exchange.getQueryString() : oqs;
    }
}
