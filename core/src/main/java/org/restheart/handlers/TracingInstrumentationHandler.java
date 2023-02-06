/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

    public TracingInstrumentationHandler() {
        var _th = Bootstrapper.getConfiguration().logging().tracingHeaders();
        this.traceHeaders = _th == null ? new ArrayList<>() : _th;
    }

    public TracingInstrumentationHandler(final PipelinedHandler next) {
        super(next);
        var _th = Bootstrapper.getConfiguration().logging().tracingHeaders();
        this.traceHeaders = _th == null ? new ArrayList<>() : _th;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        this.traceHeaders
            .forEach((traceIdHeader) -> {Optional.ofNullable(exchange.getRequestHeaders()
                .get(traceIdHeader))
                .flatMap(x -> Optional.ofNullable(x.peekFirst()))
                .ifPresent(value -> {
                    // saves the MDC Context
                    // see response.getMDCContext() javadoc
                    MDC.put(traceIdHeader, value);
                    ByteArrayProxyResponse.of(exchange).setMDCContext(MDC.getCopyOfContextMap());
                    exchange.getResponseHeaders().put(HttpString.tryFromString(traceIdHeader), value);
                });
            });

        if (!exchange.isResponseComplete() && getNext() != null) {
            next(exchange);
        }

        Bootstrapper.getConfiguration().logging().tracingHeaders().forEach((traceIdHeader) -> MDC.remove(traceIdHeader));
    }
}
