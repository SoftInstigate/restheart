package org.restheart.mongodb.handlers.metrics;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.Optional;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.Bootstrapper;
import org.restheart.mongodb.Configuration;
import org.slf4j.MDC;

/**
 * Handler to write tracing headers to the logging MDC. Pick it up via the
 * default way with "%X{name}", e.g. "%X{x-b3-traceid}".
 */
public class TracingInstrumentationHandler extends PipelinedHandler {
    /**
     *
     */
    public TracingInstrumentationHandler() {
        this(null);
    }
    
    /**
     *
     * @param next
     */
    public TracingInstrumentationHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Configuration.get().getTraceHeaders()
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
            next(exchange);
        }

        Configuration.get()
                .getTraceHeaders().forEach((traceIdHeader) -> {
                    MDC.remove(traceIdHeader);
                });
    }
}
