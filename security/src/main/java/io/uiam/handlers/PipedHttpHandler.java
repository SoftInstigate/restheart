package io.uiam.handlers;

import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import io.uiam.handlers.security.AccessManagerHandler;
import io.uiam.handlers.security.AuthTokenInjecterHandler;
import io.uiam.handlers.security.AuthenticationCallHandler;
import io.uiam.handlers.security.AuthenticationConstraintHandler;
import io.uiam.handlers.security.AuthenticationMechanismsHandler;
import io.uiam.handlers.security.SecurityInitialHandler;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.uiam.plugins.authorization.PluggableAccessManager;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class PipedHttpHandler implements HttpHandler {

    protected static final String CONTENT_TYPE = "contentType";

    protected static PipedHttpHandler buildSecurityHandlerChain(
            PipedHttpHandler next,
            final List<PluggableAuthenticationMechanism> mechanisms,
            final PluggableAccessManager accessManager,
            final IdentityManager identityManager) {
        PipedHttpHandler handler;

        if (accessManager == null) {
            throw new IllegalArgumentException("Error, accessManager cannot "
                    + "be null. "
                    + "Eventually use FullAccessManager "
                    + "that gives full access power ");
        }

        handler = new AuthTokenInjecterHandler(
                new AccessManagerHandler(accessManager, next));

        handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE,
                identityManager,
                new AuthenticationMechanismsHandler(
                        new AuthenticationConstraintHandler(
                                new AuthenticationCallHandler(handler),
                                accessManager),
                        mechanisms));

        return handler;
    }

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
