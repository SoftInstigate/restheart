/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.security;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface AccessManager {

    /**
     *
     * @param exchange
     * @param context
     * @return true if request is allowed
     */
    boolean isAllowed(HttpServerExchange exchange, RequestContext context);
    
    /**
     *
     * @param exchange
     * @return true if not authenticated user won't be allowed
     */
    boolean isAuthenticationRequired(final HttpServerExchange exchange);
}
