/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 SoftInstigate
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
package org.restheart.test.plugins.interceptors;

import static org.restheart.plugins.InterceptPoint.RESPONSE;

import org.restheart.exchange.Exchange;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

import io.undertow.util.HttpString;

/**
 * Stamps every response under {@code /test-mcp-callapi} with a header saying whether the
 * request came in-process. {@code McpCallApiIT} reads it to prove that a {@code call_api}
 * request runs through the interceptor chain exactly as the same request over REST does.
 */
@RegisterPlugin(
        name = "callApiStampInterceptor",
        description = "stamps responses under /test-mcp-callapi, for McpCallApiIT",
        interceptPoint = RESPONSE)
public class CallApiStampInterceptor implements WildcardInterceptor {
    public static final HttpString STAMP = HttpString.tryFromString("X-Test-Interceptor");

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        response.getHeaders().put(STAMP, Exchange.isInProcess(request.getExchange()) ? "in-process" : "listener");
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        return request.getPath().startsWith("/test-mcp-callapi");
    }
}
