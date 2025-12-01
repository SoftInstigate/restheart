/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
import io.undertow.util.HttpString;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.restheart.Bootstrapper;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.slf4j.MDC;
public class TracingInstrumentationHandler extends PipelinedHandler {
    private final List<String> traceHeaders;
    final private boolean emptyTraceHeaders;

    public TracingInstrumentationHandler() {
        var _th = Bootstrapper.getConfiguration().logging().tracingHeaders();
        this.traceHeaders = _th == null ? new ArrayList<>() : _th;
        this.emptyTraceHeaders = this.traceHeaders.isEmpty();
    }

    public TracingInstrumentationHandler(final PipelinedHandler next) {
        super(next);
        var _th = Bootstrapper.getConfiguration().logging().tracingHeaders();
        this.traceHeaders = _th == null ? new ArrayList<>() : _th;
        this.emptyTraceHeaders = this.traceHeaders.isEmpty();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
		// add traceId (last 4 chars of requestId) to the MDC
		var requestId = exchange.getRequestId();
		MDC.put("traceId", requestId.substring(Math.max(0, requestId.length() - 4)));

		if (emptyTraceHeaders) {
            // Save the MDC context (including traceId) for async operations
            ByteArrayProxyResponse.of(exchange).setMDCContext(MDC.getCopyOfContextMap());
            next(exchange);
        } else {
            this.traceHeaders
                .forEach((traceIdHeader) -> {Optional.ofNullable(exchange.getRequestHeaders()
                    .get(traceIdHeader))
                    .flatMap(x -> Optional.ofNullable(x.peekFirst()))
                    .ifPresent(value -> {
                        MDC.put(traceIdHeader, value);
                        exchange.getResponseHeaders().put(HttpString.tryFromString(traceIdHeader), value);
                    });
                });

            // Save the MDC context (including traceId and custom headers) for async operations
            ByteArrayProxyResponse.of(exchange).setMDCContext(MDC.getCopyOfContextMap());

            if (!exchange.isResponseComplete() && getNext() != null) {
                next(exchange);
            }

            this.traceHeaders.forEach(MDC::remove);
        }
    }
}
