/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers;

import com.mongodb.BasicDBObject;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.idm.IdentityManager;
import org.restheart.handlers.document.DocumentRepresentationFactory;
import org.restheart.utils.HttpStatus;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.net.URISyntaxException;
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
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class PipedHttpHandler implements HttpHandler {

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
    public abstract void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        handleRequest(exchange, null);
    }

    protected static void sendWarnings(int SC, HttpServerExchange exchange, RequestContext context) throws IllegalQueryParamenterException, URISyntaxException {
        if (SC == HttpStatus.SC_NO_CONTENT) {
            exchange.setResponseCode(HttpStatus.SC_OK);
        } else {
            exchange.setResponseCode(SC);
        }

        DocumentRepresentationFactory rf = new DocumentRepresentationFactory();
        rf.sendRepresentation(exchange, context, rf.getRepresentation(exchange.getRequestPath(), exchange, context, new BasicDBObject()));
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
    
    protected static PipedHttpHandler buildSecurityHandlerChain(PipedHttpHandler next, final AccessManager accessManager, final IdentityManager identityManager, final List<AuthenticationMechanism> mechanisms) {
        PipedHttpHandler handler;
        
        if (accessManager == null) {
            throw new IllegalArgumentException("Error, accessManager cannot be null. Eventually use FullAccessManager that gives full access power ");
        }

        handler = new AuthTokenInjecterHandler(new AccessManagerHandler(accessManager, next));
        
        handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE,
                identityManager,
                new AuthenticationMechanismsHandler(
                        new AuthenticationConstraintHandler(
                                new AuthenticationCallHandler(handler), accessManager), mechanisms));
        
        return handler;
    }
}
