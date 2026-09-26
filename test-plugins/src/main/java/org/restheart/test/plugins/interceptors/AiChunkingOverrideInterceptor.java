/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

/**
 * Test interceptor that stands in for a multi-tenant deployment, which decides per tenant whether
 * files are chunked in the background and how many at once (#758).
 *
 * <p>Activates only when the request carries {@code _ai-chunking-async=true|false} and/or
 * {@code _ai-chunking-max-concurrent=<n>}, attaching {@code override-ai-chunking-async} and/or
 * {@code override-ai-chunking-max-concurrent}. Every other request is untouched, so no other test
 * is affected.
 *
 * <p>Used by karate/ai/chunking-async.feature.
 */
@RegisterPlugin(
        name = "aiChunkingOverrideInterceptor",
        description = "Attaches override-ai-chunking-async / override-ai-chunking-max-concurrent per-request when ?_ai-chunking-async=<bool> / ?_ai-chunking-max-concurrent=<n> is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class AiChunkingOverrideInterceptor implements WildcardInterceptor {

    private static final String ASYNC_QPARAM = "_ai-chunking-async";
    private static final String MAX_CONCURRENT_QPARAM = "_ai-chunking-max-concurrent";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        var async = first(req, ASYNC_QPARAM);
        if (async != null) {
            req.attachParam("override-ai-chunking-async", Boolean.parseBoolean(async.strip()));
        }
        var max = first(req, MAX_CONCURRENT_QPARAM);
        if (max != null) {
            try {
                req.attachParam("override-ai-chunking-max-concurrent", Integer.parseInt(max.strip()));
            } catch (NumberFormatException nfe) {
                // not a number: leave the request as it is
            }
        }
    }

    private static String first(ServiceRequest<?> req, String qparam) {
        var values = req.getExchange().getQueryParameters().get(qparam);
        var value = values == null ? null : values.peekFirst();
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return (params.containsKey(ASYNC_QPARAM) && !params.get(ASYNC_QPARAM).isEmpty())
                || (params.containsKey(MAX_CONCURRENT_QPARAM) && !params.get(MAX_CONCURRENT_QPARAM).isEmpty());
    }
}
