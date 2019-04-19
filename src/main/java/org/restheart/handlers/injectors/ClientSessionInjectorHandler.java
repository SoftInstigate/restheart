/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.db.sessions.ClientSessionFactory;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * this handler injects the ClientSession in the request context
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ClientSessionInjectorHandler extends PipedHttpHandler {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(ClientSessionInjectorHandler.class);
    
    public static ClientSessionInjectorHandler getInstance() {
        if (ClientSessionInjectorHandlerHolder.INSTANCE == null) {
            throw new IllegalStateException("Singleton not initialized");
        }
        
        return ClientSessionInjectorHandlerHolder.INSTANCE;
    }

    private static class ClientSessionInjectorHandlerHolder {
        private static ClientSessionInjectorHandler INSTANCE = null;
    }
    
    public static void build(PipedHttpHandler next) {
        if (ClientSessionInjectorHandlerHolder.INSTANCE != null) {
            throw new IllegalStateException("Singleton already initialized");
        }
        
        ClientSessionInjectorHandlerHolder.INSTANCE 
                = new ClientSessionInjectorHandler(next);
    }
    
    private ClientSessionFactory clientSessionFactory 
            = ClientSessionFactory.getInstance();
    
    /**
     * Creates a new instance of DbPropsInjectorHandler
     *
     * @param next
     */
    private ClientSessionInjectorHandler(PipedHttpHandler next) {
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
        if (context.isInError()
                || !exchange.getQueryParameters()
                        .containsKey(RequestContext.CLIENT_SESSION_KEY)) {
            next(exchange, context);
            return;
        }

        try {
            context.setClientSession(getClientSessionFactory()
                    .getClientSession(exchange));
        } catch (IllegalArgumentException ex) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    ex.getMessage());
            next(exchange, context);
            return;
        }

        next(exchange, context);
    }

    /**
     * @return the clientSessionFactory
     */
    public ClientSessionFactory getClientSessionFactory() {
        return clientSessionFactory;
    }

    /**
     * @param clientSessionFactory the clientSessionFactory to set
     */
    public void setClientSessionFactory(ClientSessionFactory clientSessionFactory) {
        this.clientSessionFactory = clientSessionFactory;
    }
}
