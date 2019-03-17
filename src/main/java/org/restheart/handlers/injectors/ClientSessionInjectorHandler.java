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
import java.util.UUID;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.db.sessions.ClientSessionFactory;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * this handler injects the db properties in the RequestContext this handler is
 * also responsible of sending NOT_FOUND in case of requests involving not
 * existing dbs (that are not PUT)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ClientSessionInjectorHandler extends PipedHttpHandler {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(ClientSessionInjectorHandler.class);

    /**
     * Creates a new instance of DbPropsInjectorHandler
     *
     * @param next
     */
    public ClientSessionInjectorHandler(PipedHttpHandler next) {
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

        String sid = exchange.getQueryParameters()
                .get(RequestContext.CLIENT_SESSION_KEY).getFirst();

        try {
            UUID.fromString(sid);
        } catch (IllegalArgumentException iae) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "Invalid session id");
            next(exchange, context);
            return;
        }

        LOGGER.debug("Request is executed in session {}", sid);
        
        var cs = ClientSessionFactory
                .getClientSession(sid);
        
        cs.setMessageSentInCurrentTransaction(true);
        cs.startTransaction();
        
        context.setClientSession(cs);

        next(exchange, context);
    }
}
