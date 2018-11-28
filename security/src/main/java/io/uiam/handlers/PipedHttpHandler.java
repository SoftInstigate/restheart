package io.uiam.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class PipedHttpHandler implements HttpHandler {

    protected static final String CONTENT_TYPE = "contentType";

    private final PipedHttpHandler next;

    /**
     * Creates a default instance of PipedHttpHandler with next = null
     */
    public PipedHttpHandler() {
        this(null);
    }

    /**
     *
     * @param next the next handler in this chain
     */
    public PipedHttpHandler(PipedHttpHandler next) {
        this.next = next;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    public abstract void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        handleRequest(exchange, null);
    }

    /**
     * @return the next PipedHttpHandler
     */
    protected PipedHttpHandler getNext() {
        return next;
    }

    protected void next(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }
}
