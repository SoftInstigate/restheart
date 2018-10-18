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
package io.uiam.utils;

import io.undertow.server.HttpServerExchange;

/**
 * ResponseHelper mock implementation for testing only
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class ResponseHelper {

    public static void endExchangeWithMessage(HttpServerExchange exchange, int code, String message) {
        exchange.setStatusCode(code);
    }

    private ResponseHelper() {
    }

}
