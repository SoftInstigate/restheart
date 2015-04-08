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
package org.restheart.security.handlers;

import org.restheart.handlers.PipedHttpHandler;
import org.restheart.security.AccessManager;
import org.restheart.utils.HttpStatus;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class AccessManagerHandler extends PipedHttpHandler {

    private final AccessManager accessManager;

    /**
     * Creates a new instance of AccessManagerHandler
     *
     * @param accessManager
     * @param next
     */
    public AccessManagerHandler(AccessManager accessManager, PipedHttpHandler next) {
        super(next);
        this.accessManager = accessManager;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (accessManager.isAllowed(exchange, context)) {
            if (getNext() != null) {
                getNext().handleRequest(exchange, context);
            }
        } else {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_FORBIDDEN);
        }
    }
}
