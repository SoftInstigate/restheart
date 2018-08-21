package org.restheart.handlers;

import com.google.common.annotations.VisibleForTesting;

import org.restheart.Bootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HexConverter;
import io.undertow.util.HttpString;

/**
 * Handler to write tracing headers to the logging MDC. Pick it up via the default way with "%X{name}", e.g. "%X{x-b3-traceid}".
 */
public class TracingInstrumentationHandler extends PipedHttpHandler {
    public TracingInstrumentationHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        for (String traceIdHeader: Bootstrapper.getConfiguration().getTraceHeaders()) {
            Optional.ofNullable(exchange.getRequestHeaders().get(traceIdHeader))
                .flatMap(x -> Optional.ofNullable(x.peekFirst()))
                .ifPresent(value -> {
                    MDC.put(traceIdHeader, value);
                    exchange.getResponseHeaders().put(HttpString.tryFromString(traceIdHeader), value);
                });
        }

        if (!exchange.isResponseComplete() && getNext() != null) {
            next(exchange, context);
        }
    }
}
