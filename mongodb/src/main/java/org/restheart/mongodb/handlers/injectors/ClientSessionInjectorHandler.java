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
package org.restheart.mongodb.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.restheart.mongodb.db.sessions.ClientSessionFactory;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import static org.restheart.handlers.exchange.ExchangeKeys.CLIENT_SESSION_KEY;
import org.restheart.utils.HttpStatus;
import org.restheart.mongodb.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * this handler injects the ClientSession in the request context
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ClientSessionInjectorHandler extends PipelinedHandler {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(ClientSessionInjectorHandler.class);
    
    /**
     *
     * @return
     */
    public static ClientSessionInjectorHandler getInstance() {
        if (ClientSessionInjectorHandlerHolder.INSTANCE == null) {
            throw new IllegalStateException("Singleton not initialized");
        }
        
        return ClientSessionInjectorHandlerHolder.INSTANCE;
    }

    private static class ClientSessionInjectorHandlerHolder {
        private static ClientSessionInjectorHandler INSTANCE = null;
    }
    
    /**
     *
     * @param next
     */
    public static void build(PipelinedHandler next) {
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
    private ClientSessionInjectorHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        
        if (request.isInError()
                || !exchange.getQueryParameters()
                        .containsKey(CLIENT_SESSION_KEY)) {
            next(exchange);
            return;
        }

        try {
            request.setClientSession(getClientSessionFactory()
                    .getClientSession(exchange));
        } catch (IllegalArgumentException ex) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    ex.getMessage());
            next(exchange);
            return;
        }

        next(exchange);
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
