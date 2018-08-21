package org.restheart.handlers;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.Database;
import org.restheart.db.DbsDAO;
import org.restheart.db.OperationResult;
import org.restheart.handlers.metadata.InvalidMetadataException;
import org.restheart.metadata.Relationship;
import org.restheart.metadata.checkers.RequestChecker;
import org.restheart.metadata.transformers.RequestTransformer;
import org.restheart.security.AccessManager;
import org.restheart.security.handlers.AccessManagerHandler;
import org.restheart.security.handlers.AuthTokenInjecterHandler;
import org.restheart.security.handlers.AuthenticationCallHandler;
import org.restheart.security.handlers.AuthenticationConstraintHandler;
import org.restheart.security.handlers.AuthenticationMechanismsHandler;
import org.restheart.security.handlers.SecurityInitialHandler;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class PipedHttpHandler implements HttpHandler {

    protected static final String PROPERTIES = "properties";
    protected static final String FILE_METADATA = "metadata";
    protected static final String _ID = "_id";
    protected static final String CONTENT_TYPE = "contentType";
    protected static final String FILENAME = "filename";

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

    protected boolean isInvalidMetadata(BsonDocument content, HttpServerExchange exchange, RequestContext context) throws Exception {
        // check RELS metadata
        if (content.containsKey(Relationship.RELATIONSHIPS_ELEMENT_NAME)) {
            try {
                Relationship.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong relationships definition. "
                        + ex.getMessage(), ex);
                next(exchange, context);
                return true;
            }
        }
        // check RT metadata
        if (content.containsKey(RequestTransformer.RTS_ELEMENT_NAME)) {
            try {
                RequestTransformer.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong representation transformer definition. "
                        + ex.getMessage(), ex);
                next(exchange, context);
                return true;
            }
        }
        // check SC metadata
        if (content.containsKey(RequestChecker.ROOT_KEY)) {
            try {
                RequestChecker.getFromJson(content);
            } catch (InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "wrong checker definition. "
                        + ex.getMessage(), ex);
                next(exchange, context);
                return true;
            }
        }
        return false;
    }

    protected boolean isNotAcceptableContent(BsonValue _content, HttpServerExchange exchange, RequestContext context) throws Exception {
        // cannot proceed with no data
        if (_content == null) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            next(exchange, context);
            return true;
        }
        // cannot proceed with an array
        if (!_content.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data must be a json object");
            next(exchange, context);
            return true;
        }
        if (_content.asDocument().isEmpty()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            next(exchange, context);
            return true;
        }
        return false;
    }

    protected boolean isResponseInConflict(RequestContext context, OperationResult result, HttpServerExchange exchange) throws Exception {
        context.setDbOperationResult(result);
        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }
        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_CONFLICT,
                    "The document's ETag must be provided using the '"
                    + Headers.IF_MATCH
                    + "' header");
            next(exchange, context);
            return true;
        }
        // handle the case of duplicate key error
        if (result.getHttpCode() == HttpStatus.SC_EXPECTATION_FAILED) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_EXPECTATION_FAILED,
                    "A duplicate key error occurred. "
                    + "The patched document does not fulfill "
                    + "an unique index constraint");
            next(exchange, context);
            return true;
        }
        return false;
    }
}
