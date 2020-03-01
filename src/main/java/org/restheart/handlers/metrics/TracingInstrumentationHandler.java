package org.restheart.handlers.metrics;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.Optional;
import org.restheart.Bootstrapper;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.slf4j.MDC;

/**
 * Handler to write tracing headers to the logging MDC. Pick it up via the
 * default way with "%X{name}", e.g. "%X{x-b3-traceid}".
 */
public class TracingInstrumentationHandler extends PipedHttpHandler {

    /**
     *
     * @param next
     */
    public TracingInstrumentationHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange,
            RequestContext context) throws Exception {
        Bootstrapper.getConfiguration().getTraceHeaders()
                .forEach((traceIdHeader) -> {
                    Optional.ofNullable(exchange.getRequestHeaders()
                            .get(traceIdHeader))
                            .flatMap(x -> Optional.ofNullable(x.peekFirst()))
                            .ifPresent(value -> {
                                MDC.put(traceIdHeader, value);
                                exchange.getResponseHeaders()
                                        .put(HttpString
                                                .tryFromString(traceIdHeader),
                                                value);
                            });
                });

        if (!exchange.isResponseComplete() && getNext() != null) {
            next(exchange, context);
        }

        Bootstrapper.getConfiguration()
                .getTraceHeaders().forEach((traceIdHeader) -> {
                    MDC.remove(traceIdHeader);
                });
    }
}
