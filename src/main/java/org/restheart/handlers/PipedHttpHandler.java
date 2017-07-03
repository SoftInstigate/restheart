package org.restheart.handlers;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.restheart.db.Database;
import org.restheart.db.DbsDAO;
import org.restheart.security.AccessManager;
import org.restheart.security.handlers.AccessManagerHandler;
import org.restheart.security.handlers.AuthTokenInjecterHandler;
import org.restheart.security.handlers.AuthenticationCallHandler;
import org.restheart.security.handlers.AuthenticationConstraintHandler;
import org.restheart.security.handlers.AuthenticationMechanismsHandler;
import org.restheart.security.handlers.SecurityInitialHandler;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class PipedHttpHandler implements HttpHandler {

    protected static PipedHttpHandler buildSecurityHandlerChain(
            PipedHttpHandler next,
            final AccessManager accessManager,
            final IdentityManager identityManager,
            final List<AuthenticationMechanism> mechanisms) {
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

    private final Database dbsDAO;
    private final PipedHttpHandler next;

    /**
     * Creates a default instance of PipedHttpHandler with next = null and
     * dbsDAO = new DbsDAO()
     */
    public PipedHttpHandler() {
        this(null, new DbsDAO());
    }

    /**
     *
     * @param next the next handler in this chain
     */
    public PipedHttpHandler(PipedHttpHandler next) {
        this(next, new DbsDAO());
    }

    /**
     * Inject a custom DbsDAO, usually a mock for testing purposes
     *
     * @param next
     * @param dbsDAO
     */
    public PipedHttpHandler(PipedHttpHandler next, Database dbsDAO) {
        this.next = next;
        this.dbsDAO = dbsDAO;
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
     * @return the dbsDAO
     */
    protected Database getDatabase() {
        return dbsDAO;
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
