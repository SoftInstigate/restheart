/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import static org.restheart.exchange.ExchangeKeys.CLIENT_SESSION_KEY;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.sessions.ClientSessionFactory;
import org.restheart.utils.HttpStatus;

/**
 *
 * this handler injects the ClientSession in the request context
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ClientSessionInjector extends PipelinedHandler {

    /**
     *
     * @return
     */
    public static ClientSessionInjector getInstance() {
        if (ClientSessionInjectorHandlerHolder.INSTANCE == null) {
            throw new IllegalStateException("Singleton not initialized");
        }

        return ClientSessionInjectorHandlerHolder.INSTANCE;
    }

    private static class ClientSessionInjectorHandlerHolder {
        private static ClientSessionInjector INSTANCE = null;
    }

    /**
     *
     * @return
     */
    public static ClientSessionInjector build() {
        if (ClientSessionInjectorHandlerHolder.INSTANCE != null) {
            throw new IllegalStateException("Singleton already initialized");
        }

        ClientSessionInjectorHandlerHolder.INSTANCE = new ClientSessionInjector();

        return ClientSessionInjectorHandlerHolder.INSTANCE;
    }

    private ClientSessionFactory clientSessionFactory = ClientSessionFactory.getInstance();

    /**
     * Creates a new instance of DbPropsInjectorHandler
     *
     * @param next
     */
    private ClientSessionInjector() {
        this(null);
    }

    /**
     * Creates a new instance of DbPropsInjectorHandler
     *
     * @param next
     */
    private ClientSessionInjector(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);

        if (request.isInError() || !exchange.getQueryParameters().containsKey(CLIENT_SESSION_KEY)) {
            next(exchange);
            return;
        }

        try {
            request.setClientSession(getClientSessionFactory().getClientSession(exchange));
        } catch (IllegalArgumentException ex) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_NOT_ACCEPTABLE, ex.getMessage());
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
