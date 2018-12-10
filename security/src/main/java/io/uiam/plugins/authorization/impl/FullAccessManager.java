/*
 * uIAM - the IAM for microservices
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
package io.uiam.plugins.authorization.impl;

import io.uiam.handlers.RequestContext;
import io.uiam.plugins.authorization.PluggableAccessManager;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class FullAccessManager implements PluggableAccessManager {
    private boolean authenticationRequired;

    /**
     * this access manager allows any operation to any user
     */
    public FullAccessManager(boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    @Override
    public boolean isAllowed(HttpServerExchange exchange, RequestContext context) {
        return true;
    }

    @Override
    public boolean isAuthenticationRequired(HttpServerExchange exchange) {
        return authenticationRequired;
    }
}