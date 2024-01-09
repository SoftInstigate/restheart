/*-
 * ========================LICENSE_START=================================
 * x-headers-to-qparams
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

package org.restheart.examples;

import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.PluginUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistry;
import static org.restheart.exchange.CORSHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name = "xHeadersToQParams",
    interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
    description = "allows to map X-RH headers to query parameters")
public class XHeadersToQParams implements WildcardInterceptor {
    @Inject("registry")
    PluginsRegistry registry;

    private static final String X_HEADER_PREFIX = "X-RH-";

    private static final Logger LOGGER = LoggerFactory.getLogger(XHeadersToQParams.class);

    record Pair(String name, Deque<String> values) {};

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        var processedHeaders = new ArrayList<String>();

        // for each request header that starts with X_HEADER_PREFIX
        // add a query parameter to the request
        // for instance X-RH-filter adds the query paramter filter
        StreamSupport.stream(request.getHeaders().spliterator(), false)
            .filter(hv -> hv.getHeaderName().toString().startsWith(X_HEADER_PREFIX) && !hv.getHeaderName().toString().equals(X_HEADER_PREFIX))
            .map(hv -> new Pair(hv.getHeaderName().toString(), hv.stream().collect(Collectors.toCollection(ArrayDeque::new))))
            .forEach(pair -> {
                var qparamName = pair.name().substring(X_HEADER_PREFIX.length(), pair.name().length());
                LOGGER.debug("setting qparam {} to {} from header {}", qparamName, pair.values(), pair.name());
                request.getQueryParameters().put(qparamName, pair.values());
                processedHeaders.add(pair.name());
            });

        // add the processed headers to the response header Access-Control-Expose-Headers
        // required by CORS
        updateAccessControlAllowHeaders(request, response, processedHeaders);
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        return true;
    }

    private void updateAccessControlAllowHeaders(ServiceRequest<?> request, ServiceResponse<?> response, List<String> processedHeaders) {
        var handlingService = PluginUtils.handlingService(registry, request.getExchange());
        var accessControlAllowHeaders = handlingService.accessControlAllowHeaders(request);

        var updatedAccessControlAllowHeaders = processedHeaders.stream()
            .filter(header -> accessControlAllowHeaders.indexOf(header) < 0)
            .collect(Collectors.joining(", "));

        updatedAccessControlAllowHeaders = accessControlAllowHeaders.concat(",").concat(updatedAccessControlAllowHeaders);

        response.getHeaders().put(ACCESS_CONTROL_EXPOSE_HEADERS, updatedAccessControlAllowHeaders);
    }
}
